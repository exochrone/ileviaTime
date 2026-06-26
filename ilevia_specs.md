# Spécifications techniques — Application ilévia

## Objectifs de l'application

Développer une application mobile native Android permettant aux usagers du réseau ilévia de consulter les horaires de passage en temps réel à un arrêt, la durée de transport entre deux arrêts et l'heure d'arrivée. L'application se concentre sur l'efficacité : accès direct aux prédictions, mémorisation des trajets fréquents et mise à jour automatique des informations sans intervention manuelle de l'utilisateur.

---

## Architecture technique

| Couche | Choix technique | Rôle |
|---|---|---|
| Présentation | Jetpack Compose + ViewModel | UI réactive, cycle de vie géré |
| Domaine | Use Cases Kotlin | Logique métier isolée et testable |
| Données | Repository pattern | Source unique de vérité |
| Persistance | Room (SQLite) | Trajets épinglés + index GTFS statique |
| Réseau | OkHttp | Téléchargement du flux binaire GTFS-RT |
| Parsing | `protobuf-kotlin` (`com.google.protobuf:protobuf-kotlin`) | Désérialisation des messages Protocol Buffers |
| Réactivité | `StateFlow` / `Flow` | Propagation des données temps réel vers l'UI |
| Injection | Hilt | Injection de dépendances |

> **Note :** Le Proto DataStore est exclu du scope initial, aucune préférence utilisateur n'est à persister.

---

## Sources de données

### GTFS statique (horaires théoriques)

- **URL :** `https://transport.data.gouv.fr/resources/81995`
- **Format :** Archive ZIP contenant des fichiers CSV normalisés (spec GTFS)
- **Contenu utilisé :** `routes.txt`, `trips.txt`, `stops.txt`, `stop_times.txt`
- **Fréquence :** Mise à jour quotidienne côté serveur. Surveiller le header HTTP `Last-Modified` pour détecter un changement et déclencher une réingestion.

### GTFS-RT (temps réel)

- **URL :** `https://proxy.transport.data.gouv.fr/resource/ilevia-lille-gtfs-rt`
- **Format :** Binaire Protocol Buffers (`.pb`), spécification GTFS-Realtime
- **Messages exploités :** `TripUpdate` > `StopTimeUpdate` (champs `arrival` et `departure`)
- **Polling :** toutes les 30 secondes, unique appel réseau partagé entre toutes les cartes épinglées

---

## Modèle de données Room

### Table `pinned_trip`

```
pinned_trip
├── id: Int (PK, autoGenerate)
├── route_id: String
├── trip_id: String
├── departure_stop_id: String
├── arrival_stop_id: String
├── transport_mode: String  // "BUS" | "TRAM"
└── pinned_at: Long         // timestamp d'épinglage (ordre d'affichage)
```

### Tables d'index GTFS statique

Ces tables sont peuplées une seule fois au premier lancement (puis à chaque changement détecté du fichier statique) à partir de l'archive GTFS.

```
route
├── route_id: String (PK)
├── route_short_name: String   // numéro/nom de ligne affiché
└── route_type: Int            // 3 = Bus, 0 = Tram (norme GTFS)

trip
├── trip_id: String (PK)
└── route_id: String (FK -> route)

stop
├── stop_id: String (PK)
└── stop_name: String

stop_time
├── trip_id: String (FK -> trip)
├── stop_id: String (FK -> stop)
└── stop_sequence: Int         // ordre de passage sur la course
```

---

## User Stories

### US 1 : Création d'un trajet personnalisé

**En tant qu'utilisateur**, je souhaite sélectionner un mode de transport, une ligne, un arrêt de départ et un arrêt d'arrivée, **afin de** visualiser les horaires de transport correspondants.

#### Règles de gestion

- La sélection est strictement séquentielle : **mode de transport > ligne > arrêt de départ > arrêt d'arrivée**.
- Les modes de transport disponibles sont **Bus** et **Tram**. La sélection d'un mode filtre la liste des lignes proposées (`route_type` = 3 pour Bus, 0 pour Tram).
- Les listes de lignes et d'arrêts sont peuplées depuis l'index Room (GTFS statique), jamais à la volée.
- L'arrêt d'arrivée doit appartenir à la même ligne que l'arrêt de départ **et** être différent de l'arrêt de départ.


#### Implémentation

- **Écran :** quatre `DropdownMenu` Compose chaînés. Chaque menu n'est activable que si le précédent a une valeur sélectionnée.
- **ViewModel (`TripCreationViewModel`) :**
  - Expose quatre `StateFlow` : `selectedMode`, `selectedRoute`, `selectedDepartureStop`, `selectedArrivalStop`.
  - Chaque sélection déclenche la recharge de la liste suivante via un `UseCase` dédié (`GetRoutesUseCase`, `GetStopsForRouteUseCase`).
  - La validation finale (bouton "Épingler") n'est activée que si les quatre champs sont renseignés et cohérents.
- **Repository (`GtfsStaticRepository`) :** interroge exclusivement Room, pas de lecture de fichier à chaud.

#### Tests

- Vérifier que la liste des lignes se filtre correctement selon le mode de transport sélectionné.
- Vérifier que la liste des arrêts d'arrivée exclut l'arrêt de départ.
- Vérifier que la liste des arrêts d'arrivée respecte l'ordre `stop_sequence`.

---

### US 2 : Visualisation des horaires temps réel

**En tant qu'utilisateur**, je souhaite visualiser les 3 prochains passages à mon arrêt de départ avec, en regard, l'horaire de passage prévu à mon arrêt d'arrivée, **afin de** connaître précisément mon temps de trajet.

#### Règles de gestion

- Afficher exactement **3 prochains passages** (paires départ/arrivée), triés par heure de départ croissante.
- Chaque ligne affiche la paire : `[heure départ arrêt 1]  →  [heure passage arrêt 2]`.
- **Règle d'affichage des durées (applicable à départ ET arrivée) :**
  - Si le temps avant passage est **inférieur à 60 minutes** : afficher la durée en minutes, ex. `3'`.
  - Si le temps avant passage est **supérieur ou égal à 60 minutes** : afficher l'heure absolue, ex. `16:54`.
  - Si la donnée temps réel est absente : afficher l'horaire théorique GTFS statique entre parenthèses, ex. `(16:54)`.
  - Si l'arrêt est un terminus ou hors course pour ce `trip_id` : afficher `N/A`.
- En cas d'indisponibilité du flux GTFS-RT : afficher le dernier résultat connu avec un indicateur visuel "données périmées" (voir US 3).

#### Implémentation

**Flux de traitement GTFS-RT :**

```
[Polling 30s] --> [OkHttp : GET flux binaire .pb]
                          |
                          v
              [Protobuf : désérialisation du FeedMessage complet]
                          |
                          v
         [Filtre 1 : ne garder que les FeedEntity de type TripUpdate]
                          |
                          v
         [Filtre 2 : ne garder que les TripUpdate dont le trip_id
          figure dans la liste des trip_id associés à la route_id
          sélectionnée (résolue depuis l'index Room)]
                          |
                          v
         [Filtre 3 : pour chaque TripUpdate retenu, extraire les
          StopTimeUpdate dont le stop_id correspond à l'arrêt de
          départ OU à l'arrêt d'arrivée]
                          |
                          v
         [Corrélation : apparier départ + arrivée sur un même trip_id
          pour former une paire (StopTimeUpdate départ, StopTimeUpdate arrivée).
          Écarter les trip_id pour lesquels l'un des deux stop_id est absent.]
                          |
                          v
         [Calcul des valeurs d'affichage :
          - delta < 60 min  => "3'"
          - delta >= 60 min => "16:54"
          - donnée RT absente => "(16:54)" depuis stop_times Room
          - stop absent de la course => "N/A"]
                          |
                          v
              [Emission via StateFlow<List<TripPassage>> vers le ViewModel]
```

- **`GtfsRtRepository` :** expose un unique `Flow<FeedMessage>` produit par un `ticker` coroutine (intervalle 30s). Ce Flow est **partagé** (`shareIn` avec `SharingStarted.WhileSubscribed`) entre tous les consommateurs actifs (cartes épinglées + écran de détail). Un seul appel réseau est effectué toutes les 30 secondes, quel que soit le nombre de trajets épinglés.
- **`GetNextPassagesUseCase` :** prend en paramètre un `PinnedTrip` et applique la chaîne de filtrage décrite ci-dessus sur le `Flow<FeedMessage>` partagé. Retourne un `Flow<List<TripPassage>>`.
- **Modèle `TripPassage` :**

```kotlin
data class TripPassage(
    val tripId: String,
    val departure: PassageTime,
    val arrival: PassageTime
)

sealed class PassageTime {
    data class RealTime(val epochSeconds: Long) : PassageTime()
    data class Scheduled(val epochSeconds: Long) : PassageTime()   // affiché entre parenthèses
    object NotAvailable : PassageTime()                             // affiché "N/A"
}
```

- **Calcul de l'affichage :** encapsulé dans un `DisplayFormatter` (objet pur, sans dépendance Android) pour faciliter les tests unitaires.

#### Tests

- Test d'intégration avec un fichier `.pb` local : vérifier la corrélation correcte départ/arrivée pour un `trip_id` donné.
- Test unitaire du `DisplayFormatter` : couvrir les quatre cas d'affichage (< 60 min, >= 60 min, théorique, N/A).
- Test unitaire de la logique d'appariement : vérifier qu'un `trip_id` sans l'un des deux `stop_id` est bien écarté.

---

### US 3 : Épinglage et suivi automatique

**En tant qu'utilisateur**, je souhaite épingler un trajet sur mon écran d'accueil, **afin de** voir mes horaires mis à jour automatiquement sans refaire la recherche.

#### Règles de gestion

- Chaque carte épinglée affiche les **3 prochains passages** (même règle qu'US 2).
- Les cartes se rafraîchissent automatiquement toutes les 30 secondes, sans action utilisateur.
- En cas d'indisponibilité du flux GTFS-RT (erreur réseau, timeout, réponse non-200) : les cartes continuent d'afficher le **dernier résultat connu** accompagné d'un indicateur visuel "données périmées" (ex. bandeau ou icône horodatée : "Dernière mise à jour : 14:32").
- La suppression d'une carte retire le `PinnedTrip` de Room et arrête immédiatement l'observation du flux associé.

#### Implémentation

- **`HomeViewModel` :**
  - Observe `pinnedTripDao.getAll()` (Flow Room) pour maintenir la liste des trajets épinglés.
  - Pour chaque `PinnedTrip` émis, instancie un `GetNextPassagesUseCase` et combine les résultats via `combine` ou `flatMapLatest`.
  - Expose un `StateFlow<List<PinnedTripUiState>>` vers l'UI.
- **`PinnedTripUiState` :**

```kotlin
data class PinnedTripUiState(
    val pinnedTrip: PinnedTrip,
    val passages: List<TripPassage>,
    val dataStatus: DataStatus
)

sealed class DataStatus {
    object Fresh : DataStatus()
    data class Stale(val lastUpdateAt: Long) : DataStatus()  // epoch seconds
    object Loading : DataStatus()
}
```

- **Gestion de la péremption :** le `GtfsRtRepository` wrape chaque émission dans un `Result<FeedMessage>`. En cas d'échec, il émet un `Result.failure` sans interrompre le Flow. Le `GetNextPassagesUseCase` propage alors un `DataStatus.Stale` avec le timestamp de la dernière émission réussie.
- **UI :** `LazyColumn` de `PinnedTripCard` composables. Chaque carte observe son propre `PinnedTripUiState`. L'indicateur "données périmées" est affiché en superposition ou en pied de carte si `dataStatus` est `Stale`.
- **Désépinglage :** un swipe ou bouton supprime la ligne dans Room via `pinnedTripDao.delete(pinnedTrip)`. Le `flatMapLatest` annule automatiquement la coroutine d'observation associée.

#### Tests

- Vérifier que la suppression d'un `PinnedTrip` dans Room annule le `Job` coroutine d'observation du flux associé.
- Vérifier que `DataStatus.Stale` est bien émis lors d'une erreur réseau et que les données précédentes sont conservées.
- Vérifier que plusieurs cartes épinglées simultanées ne génèrent qu'un seul appel réseau par cycle de 30 secondes.

---

## Initialisation de l'index GTFS statique

Au **premier lancement**, et à chaque fois que le header `Last-Modified` de la ressource GTFS statique diffère de la valeur stockée localement :

1. Télécharger l'archive ZIP depuis `https://transport.data.gouv.fr/resources/81995`.
2. Extraire et parser en streaming les fichiers `routes.txt`, `trips.txt`, `stops.txt`, `stop_times.txt`.
3. Insérer les entités en base Room via des transactions par batch (éviter les insertions ligne par ligne).
4. Persister le timestamp `Last-Modified` pour les comparaisons futures.

Cette opération doit être effectuée dans un `Worker` WorkManager (`GtfsStaticSyncWorker`) pour survivre aux interruptions de processus. L'écran de création de trajet (US 1) doit afficher un état de chargement tant que cet index n'est pas disponible.

---

## Recommandations de développement

1. **Flux partagé :** le `Flow<FeedMessage>` du `GtfsRtRepository` doit utiliser `shareIn(scope, SharingStarted.WhileSubscribed(5_000))` pour éviter les appels réseaux redondants tout en s'arrêtant proprement quand aucun abonné n'est actif.
2. **Filtrage précoce :** appliquer les filtres `trip_id` et `stop_id` immédiatement après la désérialisation Protobuf, avant toute transformation, pour minimiser la pression mémoire.
3. **Performance UI :** utiliser `LazyColumn` pour l'écran d'accueil. Chaque `PinnedTripCard` doit être un composable `key`é par `pinnedTrip.id` pour éviter les recompositions inutiles.
4. **Testabilité :** isoler le `DisplayFormatter` et les Use Cases de toute dépendance Android. Les tester avec JUnit pur, sans Robolectric.
5. **Gestion des fuseaux horaires :** les timestamps GTFS-RT sont en secondes UTC. Convertir en heure locale uniquement au moment de l'affichage, dans le `DisplayFormatter`.

---

## Précisions 

1. Dans pinned_trip on travaille sur des trajets théoriques (Ligne X, Arrêt A → Arrêt B) et l'app cherche dynamiquement les prochaines courses correspondantes.
2. Après le choix de l'arrêt 1, l'utilisateur choisi l'arrêt 2 ce qui décide de la direction du trajet. On affiche la direction en interface. 
3. Pendant le premier lancement et le chargement du GTFS statique, afficher un écran de chargement bloquant avec un indicateur animé. On supprime l'archive ZIP téléchargée dés qu'elle a été correctement importée. 
4. Le fichier proto n'est pas fourni : utiliser la définition standard de google pour GTFS-Realtime
5. Le code doit être en anglais, strings.xml en français

## URLs de référence

| Ressource | URL |
|---|---|
| GTFS statique | `https://transport.data.gouv.fr/resources/81995` |
| GTFS-RT (flux temps réel) | `https://proxy.transport.data.gouv.fr/resource/ilevia-lille-gtfs-rt` |
