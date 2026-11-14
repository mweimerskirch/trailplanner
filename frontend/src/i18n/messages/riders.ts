// Texts of the riders area (rider selection and management, rider menu); see ../index.ts.

export const en = {
  title: 'Who’s planning?',
  sub: 'Pick your name to open your library.',
  routes: '{n} route | {n} routes',
  newName: 'New name',
  renameRider: 'Rename {name}',
  deleteRider: 'Delete {name}',
  add: 'Add a rider',
  addFirst: 'Add the first rider',
  namePlaceholder: 'Name',
  manage: 'Manage riders',
  done: 'Done',
  noPasswords: 'No passwords: everyone on this network can open every library.',
  confirmDelete: {
    title: 'Delete {name}?',
    message:
      '{name} and all their routes ({routes} in the list, plus any archived or in the trash) will be deleted permanently. This cannot be undone.',
    confirm: 'Delete rider',
  },
  error: {
    unreachable: 'The server is not reachable.',
    generic: 'Something went wrong.',
  },
  menu: {
    planningAs: 'Planning as {name}',
    switch: 'Switch rider',
    profile: 'Profile',
  },
}

export const fr: typeof en = {
  title: 'Qui planifie ?',
  sub: 'Choisissez votre nom pour ouvrir votre bibliothèque.',
  routes: '{n} itinéraire | {n} itinéraires',
  newName: 'Nouveau nom',
  renameRider: 'Renommer {name}',
  deleteRider: 'Supprimer {name}',
  add: 'Ajouter un profil',
  addFirst: 'Ajouter le premier profil',
  namePlaceholder: 'Nom',
  manage: 'Gérer les profils',
  done: 'Terminé',
  noPasswords: 'Pas de mots de passe : tout le monde sur ce réseau peut ouvrir toutes les bibliothèques.',
  confirmDelete: {
    title: 'Supprimer {name} ?',
    message:
      '{name} et tous ses itinéraires ({routes} dans la liste, plus ceux archivés ou dans la corbeille) seront supprimés définitivement. Cette action est irréversible.',
    confirm: 'Supprimer le profil',
  },
  error: {
    unreachable: 'Le serveur est injoignable.',
    generic: 'Une erreur s’est produite.',
  },
  menu: {
    planningAs: 'Profil : {name}',
    switch: 'Changer de profil',
    profile: 'Profil',
  },
}

export const de: typeof en = {
  title: 'Wer plant?',
  sub: 'Wähle deinen Namen, um deine Bibliothek zu öffnen.',
  routes: '{n} Route | {n} Routen',
  newName: 'Neuer Name',
  renameRider: '{name} umbenennen',
  deleteRider: '{name} löschen',
  add: 'Profil hinzufügen',
  addFirst: 'Erstes Profil hinzufügen',
  namePlaceholder: 'Name',
  manage: 'Profile verwalten',
  done: 'Fertig',
  noPasswords: 'Keine Passwörter: Alle in diesem Netzwerk können jede Bibliothek öffnen.',
  confirmDelete: {
    title: '{name} löschen?',
    message:
      '{name} und alle Routen ({routes} in der Liste, dazu archivierte und solche im Papierkorb) werden endgültig gelöscht. Das lässt sich nicht rückgängig machen.',
    confirm: 'Profil löschen',
  },
  error: {
    unreachable: 'Der Server ist nicht erreichbar.',
    generic: 'Etwas ist schiefgelaufen.',
  },
  menu: {
    planningAs: 'Du planst als {name}',
    switch: 'Profil wechseln',
    profile: 'Profil',
  },
}
