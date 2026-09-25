# @jebooj/capacitor-pedometer

Compteur de pas interne pour Capacitor, avec **synchronisation autonome** vers un serveur.

Le plugin compte les pas dans un *foreground service* Android et les envoie à intervalle régulier
**application fermée**. C'est sa raison d'être : ne pas dépendre de l'ouverture de l'app pour tracer
et remonter l'activité.

Portage Capacitor du fork Cordova `cordova-plugin-pedometer` (branche `master`), dont il conserve le
service, la base SQLite et la synchro HTTP.

## Les deux sens de fonctionnement

Ils sont complémentaires, pas alternatifs :

| Sens | Qui agit | Quand |
| --- | --- | --- |
| **Push autonome** | le service, via `SyncWorker` | à intervalle régulier, app fermée |
| **Lecture à la demande** | l'app, via `getEntries()` | quand un écran a besoin des données, synchronisées ou non |

## Installation

```bash
npm install @jebooj/capacitor-pedometer
npx cap sync android
```

Aucune permission à déclarer côté app : le manifeste du plugin est fusionné par Gradle.

## Utilisation

```ts
import { Pedometer } from '@jebooj/capacitor-pedometer';

// 1. cibler la synchro autonome (à refaire à chaque changement d'utilisateur)
await Pedometer.configure({
  userId: '10470',
  apiUrl: 'https://startr-api.jebooj.com/v1/partners/dynafit',
  syncIntervalMinutes: 15,
});

// 2. permissions puis démarrage du service
const permissions = await Pedometer.requestPermissions();
if (permissions.activity === 'granted') {
  await Pedometer.start({ goal: 10000 });
}

// 3. mises à jour live (uniquement au premier plan ; le service compte quand même sans elles)
await Pedometer.addListener('stepsUpdate', ({ stepsToday }) => {
  console.log(stepsToday);
});

// 4. relecture de la base locale, lignes synchronisées ou non
const { entries } = await Pedometer.getEntries({
  start: Date.now() - 7 * 864e5,
  end: Date.now(),
  synced: 'all',
});

// 5. forcer un envoi immédiat (optionnel)
const { sent, pending, lastSyncAt } = await Pedometer.sync();
```

## Configuration de la synchro autonome

`configure()` écrit dans la table `settings` de la base SQLite du plugin — c'est l'équivalent du
`setConfig({userid, api})` de la version Cordova.

| Champ | Rôle |
| --- | --- |
| `userId` | identifie l'utilisateur dans la charge postée par le service |
| `apiUrl` | URL absolue de destination |
| `syncIntervalMinutes` | période souhaitée ; Android impose un plancher de **15 minutes** pour le travail périodique |

> ⚠️ Le service continue de poster vers ce qui est stocké **même après une déconnexion**. Rappeler
> `configure()` à chaque changement d'utilisateur, sinon les pas seraient attribués au compte
> précédent.

## États de synchronisation

La colonne `synced` de la table `steps`, telle que la manipulent `queueLinesToSync()`,
`updateLinesSynced()` et `rollbackLinesToSync()` :

| Valeur | Filtre `getEntries` | Sens |
| --- | --- | --- |
| `0` | `pending` | en attente d'envoi |
| `1` | `queued` | inclus dans l'envoi en cours |
| `2` | `synced` | acquitté par le serveur |

## Ce qui a changé depuis la version Cordova

| Sujet | Avant | Maintenant |
| --- | --- | --- |
| Entrée JS | `PedoListener` (Cordova) | `PedometerPlugin` (`@CapacitorPlugin`) |
| Mises à jour | callback Cordova maintenu en vie | événement `stepsUpdate` |
| Permissions | déclarées, **jamais demandées** | `ACTIVITY_RECOGNITION` et `POST_NOTIFICATIONS` demandées à l'exécution |
| Relance périodique | `AlarmManager` toutes les 2 min | `WorkManager` (`PeriodicWorkRequest`) — l'ancienne voie est refusée sur Android 12+ |
| Synchro HTTP | thread nu depuis le service | portée par le Worker, avec contrainte réseau et réessai |
| Détection réseau | `getActiveNetworkInfo()` (déprécié) | `NetworkCapabilities` |
| Base SQLite | stockage **externe** | stockage **interne** |
| Support | `android.support.*` | AndroidX |
| Lecture | `getNoSyncResults()` | `getEntries({start, end, synced})` |
| Service exporté | `exported="true"` | `exported="false"` |

Les clés de préférences et le nom de la base sont **inchangés** : une app qui migre depuis la version
Cordova conserve son objectif, ses compteurs et ses textes de notification.

## Fonctionnement permanent (Android)

L'app décide, le service tient seul :

- **Activation** : uniquement par `start()`, qui exige `ACTIVITY_RECOGNITION` et écrit
  `status_service = "start"` dans la table `settings`. **Arrêt** : uniquement par `stop()`
  (`status_service = "stop"`, service arrêté, synchro annulée). Rien d'autre ne change cet état.
- **Relances automatiques**, toutes conditionnées par `ServiceControl` (activé **et** permission
  accordée) : mise à jour de l'app, redémarrage du téléphone (après le premier déverrouillage),
  retrait de la tâche des récents, mort du process (`START_STICKY`), et à chaque retour de l'app au
  premier plan — ce dernier point rattrape un service tué par l'OEM ou par un « Forcer l'arrêt ».
- **Batterie** : `getBatteryOptimizationStatus()`, puis `requestIgnoreBatteryOptimizations()` (boîte
  de dialogue système standard) ; `openBatteryOptimizationSettings()` pour les écrans constructeur
  (Xiaomi, Huawei, Oppo…), avec repli sur la liste système. L'exemption autorise le service à revenir
  au premier plan depuis l'arrière-plan : c'est elle qui garantit la permanence sur les téléphones
  agressifs.
- Limite d'Android, non contournable : après un **« Forcer l'arrêt »** manuel, rien ne redémarre
  tant que l'utilisateur n'a pas rouvert l'app.

## API

Voir [`src/definitions.ts`](src/definitions.ts) — l'interface `PedometerPlugin` est documentée
méthode par méthode.

## iOS

Volontairement partiel. L'app Start'R lit ses pas depuis **HealthKit** sur iOS ; le podomètre interne
est une fonctionnalité **Android**. L'implémentation Swift couvre ce que `CMPedometer` sait faire
(disponibilité, mises à jour live, historique **7 jours maximum**) ; tout ce qui relève du service
Android — base locale, synchro autonome, notification, réglages batterie — répond `UNAVAILABLE`
plutôt que de faire semblant. iOS ne permet pas à une app en arrière-plan de tenir un compteur
persistant et de poster seule.

Ajouter `NSMotionUsageDescription` dans l'`Info.plist` de l'app si l'implémentation iOS est utilisée
(Capacitor n'écrit pas l'`Info.plist`).

## Play Console

Le service est de type `health`. Prévoir la déclaration du *foreground service* correspondante, avec
la vidéo de démonstration exigée par Google.

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` est soumise à la politique Play « Optimisations de la
batterie » : justifier le comptage de pas permanent en arrière-plan comme fonction principale.

## Sécurité — à savoir

Le service tourne sans l'application, donc **sans JWT valide**. L'endpoint de destination doit donc
accepter une requête non authentifiée identifiant l'utilisateur par son `userId`. Piste de
durcissement sans perdre l'autonomie : stocker un **jeton d'appareil de longue durée** dans la table
`settings` à côté de `userid`, délivré à l'enregistrement du capteur et vérifié côté serveur.

## Licence

MIT
