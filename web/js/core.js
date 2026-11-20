/* =========================================================
   ToonIA Studio — core.js
   État, persistance locale, client Gemini (proxy serveur),
   visuels procéduraux, moteur canonique.
   ========================================================= */
(function () {
  'use strict';

  const T = (window.T = {});

  /* ---------------- utilitaires ---------------- */
  T.uid = (p = 'id') => p + '_' + Math.random().toString(36).slice(2, 10);
  T.esc = (s) =>
    String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  T.clamp = (s, n) => {
    const str = String(s == null ? '' : s).trim();
    return str.length > n ? str.slice(0, n - 1) + '…' : str;
  };
  T.slug = (s) =>
    String(s || 'projet')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 48) || 'projet';

  function hashStr(str) {
    let h = 2166136261 >>> 0;
    for (let i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return h >>> 0;
  }
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  T.rngFor = (seed) => mulberry32(hashStr(String(seed || 'toonia')));
  T.pick = (rng, arr) => arr[Math.floor(rng() * arr.length) % arr.length];
  T.rotate = (rng, arr, n) => {
    const start = Math.floor(rng() * arr.length) % arr.length;
    return Array.from({ length: Math.min(n, arr.length) }, (_, i) => arr[(start + i) % arr.length]);
  };
  T.now = () => Date.now();

  /* ---------------- profils de genre ---------------- */
  const GENRES = [
    {
      id: 'cyberpunk',
      keys: ['cyber', 'punk', 'implant', 'néon', 'neon', 'hacker', 'dystop', 'corporat', 'bionique', 'androïd', 'androide', 'futur', 'quantique', 'bionique'],
      label: 'Cyberpunk / Science-fiction',
      tone: 'Sombre, tendu, mélancolique',
      subgenres: ['Dystopie technologique', 'Thriller urbain', 'Post-humanisme'],
      themes: ['la survie', 'la mémoire', 'le transhumanisme', 'la corruption'],
      hero: 'Kael', hero2: 'Mira', antagonist: 'Silas Vorn', mentor: 'Dok Henz',
      archetype: 'Mécanicien clandestin réticent',
      ability: 'Manipulation d’implants et sens augmenté',
      hair: 'noirs ébouriffés', eyes: 'bleus électriques',
      look: 'Perfecto élimé, câbles apparents sur l’avant-bras, masque filtrant',
      places: [
        { name: 'Les Bas-Fonds de Néopolis', desc: 'Dédale de ruelles saturées d’enseignes holographiques et de pluie acide.', atmos: 'Étouffante, scintillante, dangereuse' },
        { name: 'Chimera Dynamics', desc: 'Tour de verre où se décident les vies louées des habitants.', atmos: 'Clinique, froide, écrasante' },
        { name: 'Le Marché des Souvenirs', desc: 'Marché noir où l’on revend des mémoires d’occasion.', atmos: 'Fiévreuse, intime, immorale' }
      ],
      factions: 'Chimera Dynamics (corporation dominante) ; les Récupérateurs (contrebande d’implants) ; la Cellule Zéro (hackers insoumis).',
      rules: 'Toute augmentation consomme une ressource vitale. Un implant prohibé déclenche une traque automatisée en moins de six heures.',
      geography: 'Une ville verticale : les étages hauts respirent un air filtré, les niveaux bas baignent dans les infiltrations.',
      social: 'Citoyens notés par un score de crédit social ; les « sans-score » vivent hors réseau.',
      lore: 'Vingt ans plus tôt, l’effondrement du réseau central a effacé une génération de souvenirs.',
      terms: [{ term: 'Fantôme', def: 'Trace mnésique laissée par un implant retiré trop vite.' }, { term: 'Saignée', def: 'Coupure volontaire du réseau pour échapper à la traque.' }],
      constraints: 'Pas de voyage temporel ; la technologie ne résout jamais un conflit humain.',
      objects: [
        { name: 'L’implant quantique', sig: 'Objet déclencheur : il rembobine cinq secondes au prix du corps.', desc: 'Une puce triangulaire qui pulse comme un cœur malade.' },
        { name: 'Le carnet de Lena', sig: 'Preuve fragile que la version officielle du passé est fausse.', desc: 'Carnet papier tenu par une sœur disparue.' }
      ],
      conflict: 'La ville survit en effaçant la mémoire de ses habitants ; le héros doit choisir entre vérité et survie.',
      stakes: 'Si la vérité éclate, le réseau s’effondre — avec tous ceux qui en dépendent.',
      visual: { artStyle: 'Manhwa cyberpunk contrasté, encrage nerveux', palette: 'Violet néon, cyan glacé, magenta, noir profond', paneling: 'Défilement vertical, cases hautes et étroites, inserts de détail', lighting: 'Contrastes durs, sources artificielles, halos de pluie', elements: 'Pluie, néons, câbles, reflets sur le bitume', framing: 'Plans larges verticaux pour la ville, gros plans sur les yeux', continuity: 'Implants toujours visibles à gauche du visage' },
      humor: { style: 'Ironie cynique, humour de survie', gags: 'Le distributeur qui refuse la monnaie trouée', hooks: 'Mira commente chaque situation comme un tutoriel mal écrit.', situations: 'Négociations avec des criminels trop polis', freq: 'Une réplique toutes les 3 planches' }
    },
    {
      id: 'fantasy',
      keys: ['fantasy', 'magie', 'magique', 'royaume', 'dragon', 'épée', 'epee', 'sorcier', 'elfe', 'chevalier', 'démon', 'demon', 'mythe', 'prophétie', 'prophetie'],
      label: 'Fantasy / Aventure',
      tone: 'Épique, chaleureux, traversé d’ombres',
      subgenres: ['Dark fantasy', 'Quête initiatique', 'Low fantasy'],
      themes: ['le prix du pouvoir', 'la loyauté', 'l’héritage', 'la rédemption'],
      hero: 'Aiden', hero2: 'Naya', antagonist: 'Le Roi Cendré', mentor: 'Mestre Orik',
      archetype: 'Mage d’élite réticent',
      ability: 'Tissage de lumière et lecture des serments',
      hair: 'noirs ébouriffés', eyes: 'bleus électriques',
      look: 'Manteau de voyage rapiécé, gantelet de cuir gravé de runes',
      places: [
        { name: 'La Cité-Sous-Verne', desc: 'Cité bâtie dans les racines d’un arbre-monde fissuré.', atmos: 'Lumineuse, suspendue, inquiète' },
        { name: 'Les Marches de Cendre', desc: 'Plaine grise où les batailles anciennes n’ont jamais pourri.', atmos: 'Silencieuse, sèche, sacrée' },
        { name: 'Le Puits des Serments', desc: 'Puits où chaque promesse prononcée laisse une marque.', atmos: 'Fraîche, vertigineuse, solennelle' }
      ],
      factions: 'L’Ordre du Verne (gardien des serments) ; la Maison Cendrée (usurpateurs) ; les Sans-Nom (mages renégats).',
      rules: 'Un serment prononcé au Puits devient physique : le trahir brûle la peau. La magie se paie en souvenirs.',
      geography: 'Un continent autour de l’Arbre-Monde, relié par des racines praticables.',
      social: 'Noblesse de serment ; les mages sont craints et loués.',
      lore: 'Il y a mille ans, l’Arbre-Monde fut fendu pour arrêter une guerre — personne ne se souvient de qui a frappé.',
      terms: [{ term: 'Serment noué', def: 'Promesse devenue contrainte magique.' }, { term: 'Cendre vive', def: 'Résidu magique qui garde la chaleur des morts.' }],
      constraints: 'Aucune résurrection ; la magie ne crée jamais de matière, elle déplace.',
      objects: [
        { name: 'La Lame du Premier Serment', sig: 'Arme et preuve dynastique.', desc: 'Épée dont la garde porte un serment illisible.' },
        { name: 'Le Registre des Noms', sig: 'Quiconque y est rayé disparaît des mémoires.', desc: 'Livre relié de cuir clair, anormalement lourd.' }
      ],
      conflict: 'Le royaume tient par des serments que plus personne ne comprend ; le héros hérite du plus dangereux.',
      stakes: 'Si l’Arbre-Monde meurt, les serments se brisent et la magie se retourne contre les vivants.',
      visual: { artStyle: 'Webtoon fantasy semi-réaliste, textures d’aquarelle', palette: 'Vert profond, or pâle, ambre, nuit bleue', paneling: 'Cases amples, respirations paysagères', lighting: 'Lumière filtrée par les feuilles, contre-jours dorés', elements: 'Feuilles, runes, poussière en suspension', framing: 'Plans larges pour les lieux, cadrages hauts pour l’autorité', continuity: 'Runes du gantelet toujours sur l’avant-bras droit' },
      humor: { style: 'Humour de compagnonnage, autodérision', gags: 'Le cheval qui refuse obstinément de traverser l’eau', hooks: 'Naya transforme chaque prophétie en problème administratif.', situations: 'Conseils de guerre absurdes, marchandage avec des esprits', freq: 'Une respiration comique par scène' }
    },
    {
      id: 'romance',
      keys: ['romance', 'amour', 'amoureux', 'lycée', 'lycee', 'collège', 'coeur', 'cœur', 'sentiment', 'couple', 'romantique', 'rupture'],
      label: 'Romance contemporaine',
      tone: 'Doux, lumineux, nuancé de mélancolie',
      subgenres: ['Slow burn', 'Romance scolaire', 'Tranche de vie'],
      themes: ['la confiance', 'le pardon', 'l’image de soi', 'les non-dits'],
      hero: 'Léa', hero2: 'Noah', antagonist: 'Camille', mentor: 'Mme Ravel',
      archetype: 'Perfectionniste qui s’efface',
      ability: 'Écoute et mémoire des détails',
      hair: 'bruns bouclés', eyes: 'marron chaleureux',
      look: 'Chemise trop grande, carnet corné, bracelet offert',
      places: [
        { name: 'Le toit du lycée', desc: 'Espace interdit où l’on vient respirer loin du bruit.', atmos: 'Paisible, dorée, secrète' },
        { name: 'La librairie du canal', desc: 'Ancienne péniche transformée en librairie de quartier.', atmos: 'Chaleureuse, poussiéreuse, rassurante' },
        { name: 'Le quai des brumes', desc: 'Quai de béton où le fleuve avale les confidences.', atmos: 'Grise, intime, suspendue' }
      ],
      factions: 'Le club de théâtre (refuge) ; le groupe de musique de Noah ; la famille de Léa (pression scolaire).',
      rules: 'Aucun drame artificiel : chaque conflit vient d’un besoin non dit, jamais d’un malentendu commode.',
      geography: 'Un quartier de ville moyenne, autour d’un canal et d’un lycée.',
      social: 'Réputation scolaire très surveillée ; les apparences pèsent plus que les faits.',
      lore: 'Un été de rupture a laissé un silence de deux ans entre les deux personnages.',
      terms: [{ term: 'La liste', def: 'Liste de choses à faire avant la fin du lycée, écrite à deux.' }],
      constraints: 'Pas de triangle amoureux toxique ; les émotions doivent être explicites ou montrées.',
      objects: [
        { name: 'Le carnet partagé', sig: 'Un seul carnet circule entre eux, chacun n’écrit qu’une page sur deux.', desc: 'Carnet à spirale, couverture tachée de café.' },
        { name: 'Le bracelet de Léa', sig: 'Preuve d’un lien que personne n’ose nommer.', desc: 'Fine tresse de cuir avec une perle bleue.' }
      ],
      conflict: 'Léa protège son avenir scolaire en s’interdisant d’aimer ; Noah protège sa musique en refusant de rester.',
      stakes: 'Choisir entre la personne qu’on veut être et celle qu’on attend de vous.',
      visual: { artStyle: 'Romance pastel, trait doux et lumineux', palette: 'Rose poudré, crème, bleu ciel, terracotta', paneling: 'Cases horizontales, beaucoup d’air, inserts de mains', lighting: 'Heure dorée, lumière de fin d’après-midi', elements: 'Pétales, pluie fine, reflets du canal', framing: 'Gros plans sur les regards, cadrages derrière l’épaule', continuity: 'Bracelet visible à chaque scène de Léa' },
      humor: { style: 'Humour tendre, gêne adorable', gags: 'Les messages réécrits huit fois avant envoi', hooks: 'Noah cite des paroles de chansons comme s’il parlait normalement.', situations: 'Répétitions de théâtre catastrophiques, malentendus de librairie', freq: 'Léger, une scène sur deux' }
    },
    {
      id: 'horreur',
      keys: ['horreur', 'peur', 'monstre', 'fantôme', 'fantome', 'zombie', 'malédiction', 'malediction', 'sang', 'cauchemar', 'mystérieux', 'mysterieux', 'disparition', 'hanté', 'hante'],
      label: 'Horreur / Mystère',
      tone: 'Anxiogène, silencieux, oppressant',
      subgenres: ['Horreur psychologique', 'Thriller rural', 'Surnaturel lent'],
      themes: ['la culpabilité', 'le secret de famille', 'la contagion', 'le déni'],
      hero: 'Iris', hero2: 'Tomas', antagonist: 'La Veilleuse', mentor: 'Père Aubin',
      archetype: 'Témoin que personne ne croit',
      ability: 'Perception des détails que les autres refusent de voir',
      hair: 'blancs prématurés', eyes: 'gris délavé',
      look: 'Manteau de pluie trop grand, lampe torche, bandage au poignet',
      places: [
        { name: 'La maison des Dormeurs', desc: 'Maison de village dont toutes les fenêtres sont condamnées de l’intérieur.', atmos: 'Humide, immobile, respirante' },
        { name: 'Le pré aux cloches', desc: 'Champ de cloches enterrées qui sonnent sans vent.', atmos: 'Blême, brumeuse, silencieuse' },
        { name: 'Le couloir des archives', desc: 'Sous-sol municipal où les registres de naissance manquent une année entière.', atmos: 'Étouffante, poussiéreuse, menaçante' }
      ],
      factions: 'Le conseil du village (silence organisé) ; les Dormeurs (ceux qui ont vu et accepté) ; la Veilleuse (présence).',
      rules: 'La chose ne franchit jamais un seuil sans y avoir été invitée. Ce qui est vu ne peut plus être oublié.',
      geography: 'Un village isolé de 300 habitants, coupé par la crue deux mois par an.',
      social: 'Tout le monde sait ; personne ne parle. La politesse est une forme de terreur.',
      lore: 'L’année 1979 manque aux registres, et douze familles portent le même deuil.',
      terms: [{ term: 'Veille', def: 'Nuit où une famille doit garder une lumière allumée.' }, { term: 'Dormeur', def: 'Habitant ayant accepté de ne plus voir.' }],
      constraints: 'Aucun jumpscare gratuit ; la menace ne se montre jamais entièrement.',
      objects: [
        { name: 'La cloche de bronze', sig: 'Objet qui rend le rituel possible et le condamne.', desc: 'Cloche fendue au bord coupant.' },
        { name: 'Le dossier 1979', sig: 'La preuve que la mémoire du village a été fabriquée.', desc: 'Chemise cartonnée brûlée sur un coin.' }
      ],
      conflict: 'Iris enquête sur une disparition que la ville a décidé de ne pas voir, et découvre son propre rôle dans l’oubli.',
      stakes: 'Si elle parle, la chose s’intéresse à elle ; si elle se tait, elle devient complice.',
      visual: { artStyle: 'Horreur encrée, noirs profonds, grain analogique', palette: 'Blanc cassé, vert maladif, rouille, encre', paneling: 'Cases resserrées, vides verticaux, ellipses', lighting: 'Lumière rasante, halos faibles, ombres longues', elements: 'Brouillard, poussière, insectes, pluie', framing: 'Plans fixes et caméras hautes, hors-champ systématique', continuity: 'Bandage au poignet toujours présent' },
      humor: { style: 'Humour noir très rare, soupape de tension', gags: 'Le chat du presbytère qui juge tout le monde', hooks: 'Tomas fait une blague inappropriée chaque fois qu’il a peur.', situations: 'Repas de village poliment sinistres', freq: 'Une par chapitre maximum' }
    },
    {
      id: 'action',
      keys: ['shonen', 'combat', 'tournoi', 'héros', 'heros', 'pouvoir', 'super', 'école', 'ecole', 'guilde', 'chasseur', 'niveau', 'donjon', 'art martial'],
      label: 'Action / Shonen',
      tone: 'Énergique, franc, généreux',
      subgenres: ['Nekketsu', 'Tournoi', 'Progression de puissance'],
      themes: ['le dépassement', 'l’amitié', 'la justice', 'la rivalité'],
      hero: 'Ren', hero2: 'Sora', antagonist: 'Kurogane', mentor: 'Maître Ito',
      archetype: 'Combattant instinctif sous-estimé',
      ability: 'Absorption d’impact et contre-attaque instantanée',
      hair: 'roux en bataille', eyes: 'ambre doré',
      look: 'Veste d’entraînement déchirée, bandages aux poings',
      places: [
        { name: 'L’arène des Trois Lunes', desc: 'Colisée suspendu où se jouent les qualifications.', atmos: 'Électrique, poussiéreuse, bruyante' },
        { name: 'Le dojo effondré', desc: 'Ruine d’un dojo qui fut la fierté du quartier.', atmos: 'Nostalgique, silencieuse, brûlante' },
        { name: 'La faille du Nord', desc: 'Frontière où apparaissent les créatures de rang supérieur.', atmos: 'Glaciale, instable, vertigineuse' }
      ],
      factions: 'La Guilde des Porteurs (ordre officiel) ; les Sans-Rang (combattants clandestins) ; le Clan Kurogane.',
      rules: 'Un pouvoir s’éveille seulement après avoir été poussé au-delà de sa limite, et laisse une séquelle permanente.',
      geography: 'Un archipel de cités-États reliées par des portails surveillés.',
      social: 'Le rang détermine tout : logement, salaire, droit de combattre.',
      lore: 'Les portails sont apparus il y a trente ans ; la première génération de Porteurs a payé le prix.',
      terms: [{ term: 'Seuil', def: 'Moment où un pouvoir se révèle.' }, { term: 'Séquelle', def: 'Prix physique permanent d’un dépassement.' }],
      constraints: 'Pas de puissance gagnée hors écran ; chaque progression se paie.',
      objects: [
        { name: 'Les bandages du Maître', sig: 'Héritage moral et technique.', desc: 'Bandes de tissu usées, nouées d’un nœud impossible à refaire.' },
        { name: 'La clé de faille', sig: 'Permet d’ouvrir un portail interdit.', desc: 'Éclat de métal froid qui vibre près des portails.' }
      ],
      conflict: 'Ren veut prouver sa valeur sans rang, dans un système qui l’a déjà condamné.',
      stakes: 'La faille du Nord s’élargit : chaque tournoi distrait la population d’une menace réelle.',
      visual: { artStyle: 'Manhwa d’action, lignes de vitesse franches', palette: 'Orange brûlé, jaune, bleu nuit, blanc', paneling: 'Cases dynamiques, sauts de taille, gros plans d’impact', lighting: 'Contre-jours héroïques, éclairs, poussière lumineuse', elements: 'Étincelles, débris, onomatopées', framing: 'Plongées pour l’écrasement, contre-plongées pour la révélation', continuity: 'Bandages aux poings dans chaque combat' },
      humor: { style: 'Comédie de groupe, gags de rivalité', gags: 'Le classement affiché qui contredit tout le monde', hooks: 'Sora commente chaque combat comme un match de football.', situations: 'Entraînements absurdes, repas de guilde chaotiques', freq: 'Une scène légère par chapitre' }
    },
    {
      id: 'mystere',
      keys: ['mystère', 'mystere', 'enquête', 'enquete', 'détective', 'detective', 'crime', 'meurtre', 'secret', 'disparu', 'énigme', 'enigme', 'polar'],
      label: 'Thriller / Enquête',
      tone: 'Sec, précis, sous tension',
      subgenres: ['Polar procédural', 'Mystère de chambre close', 'Conspiration'],
      themes: ['la vérité', 'la corruption', 'la loyauté', 'l’aveuglement'],
      hero: 'Nora', hero2: 'Elias', antagonist: 'Le Commanditaire', mentor: 'Commissaire Bréal',
      archetype: 'Enquêtrice méthodique en disgrâce',
      ability: 'Reconstitution mentale des scènes',
      hair: 'bruns courts', eyes: 'verts attentifs',
      look: 'Trench fatigué, carnet noir, stylo toujours à la même poche',
      places: [
        { name: 'Le commissariat du 9e', desc: 'Bâtiment administratif où les dossiers sensibles s’évaporent.', atmos: 'Néons blafards, café brûlé, silence' },
        { name: 'L’hôtel Mirador', desc: 'Hôtel de luxe où la victime a été vue pour la dernière fois.', atmos: 'Feutrée, dorée, mensongère' },
        { name: 'Les docks Sud', desc: 'Zone portuaire où les caméras ne fonctionnent jamais.', atmos: 'Humide, dangereuse, anonyme' }
      ],
      factions: 'La brigade du 9e (officiel) ; le cabinet Mirador (pouvoir économique) ; les dockers (informateurs).',
      rules: 'Chaque indice est vérifiable ; aucun coupable ne repose sur une intuition non étayée.',
      geography: 'Une ville portuaire coupée en deux par un fleuve et un contrat de rénovation.',
      social: 'Une ville où l’on connaît tout le monde et où l’on se doit des services.',
      lore: 'Une affaire classée il y a douze ans refait surface par un détail identique.',
      terms: [{ term: 'Le dossier 12', def: 'Affaire non résolue qui relie tous les suspects.' }],
      constraints: 'Pas de coupable caché introduit après le dernier tiers.',
      objects: [
        { name: 'La clé de chambre 402', sig: 'Ouvre une porte qui n’existe plus sur le plan.', desc: 'Clé magnétique au numéro limé.' },
        { name: 'Le carnet noir', sig: 'Notes personnelles de l’enquêtrice, preuves de ses doutes.', desc: 'Carnet à élastique, pages cornées.' }
      ],
      conflict: 'Nora doit prouver un crime que sa hiérarchie a intérêt à enterrer.',
      stakes: 'Si elle échoue, un innocent tombe et le vrai coupable obtient le contrat du siècle.',
      visual: { artStyle: 'Thriller réaliste, encrage sec, tramage discret', palette: 'Gris bleuté, ambre, blanc froid, rouge signal', paneling: 'Cases régulières, inserts d’indices, doubles pages de reconstitution', lighting: 'Lampadaires, néons, clair-obscur', elements: 'Pluie, reflets, papiers, fumée', framing: 'Cadrages documentaires, légère contre-plongée pour le pouvoir', continuity: 'Carnet noir et stylo toujours visibles' },
      humor: { style: 'Humour pince-sans-rire', gags: 'La machine à café comme personnage secondaire', hooks: 'Elias cite des procédures inexistantes avec un sérieux total.', situations: 'Interrogatoires qui dérapent en règlements de comptes', freq: 'Une réplique sèche toutes les deux scènes' }
    }
  ];

  const DEFAULT_GENRE = GENRES[1];

  function detectProfile(project) {
    const idea = ((project && project.idea) || '').toLowerCase();
    const adv = (project && project.advanced) || {};
    const hay = [idea, adv.genre || '', adv.ambiance || '', adv.inspirations || ''].join(' ').toLowerCase();
    let best = DEFAULT_GENRE, bestScore = 0;
    for (const g of GENRES) {
      let score = 0;
      for (const k of g.keys) if (hay.includes(k)) score += 1;
      if (score > bestScore) { bestScore = score; best = g; }
    }
    return best;
  }
  T.profile = detectProfile;

  const COMMON_WORDS = new Set(['dans', 'une', 'avec', 'pour', 'mais', 'elle', 'ils', 'elles', 'nous', 'vous', 'cette', 'après', 'quand', 'chaque', 'leur', 'leurs', 'comme', 'tout', 'tous', 'plus', 'sans', 'sous', 'entre', 'alors', 'ainsi', 'depuis', 'avant', 'encore', 'même', 'aussi', 'histoire', 'jeune', 'monde', 'ville', 'projet', 'webtoon', 'héros', 'heros']);

  function extractNames(idea, profile) {
    const names = [];
    const rx = /\b([A-ZÀ-Ý][a-zà-ÿ]{2,13})\b/g;
    let m;
    while ((m = rx.exec(idea || ''))) {
      const w = m[1];
      if (COMMON_WORDS.has(w.toLowerCase())) continue;
      if (names.includes(w)) continue;
      const before = (idea || '').slice(Math.max(0, m.index - 2), m.index);
      if (before === '' && names.length === 0 && w === (idea || '').trim().split(/\s+/)[0]) continue; // premier mot de phrase
      names.push(w);
      if (names.length >= 3) break;
    }
    return names;
  }

  function firstSentence(text, fallback) {
    const clean = String(text || '').replace(/\s+/g, ' ').trim();
    if (!clean) return fallback;
    const cut = clean.split(/(?<=[.!?])\s/)[0];
    return clean.length > 12 ? cut : fallback;
  }

  /* ---------------- état & persistance ---------------- */
  const STORE_KEY = 'toonia.web.v1';

  T.blankProject = function (title, idea) {
    return {
      id: T.uid('proj'),
      title: title || 'Nouveau webtoon',
      idea: idea || '',
      advanced: {},
      analysis: null,
      characters: [],
      world: null,
      story: null,
      visual: null,
      humor: null,
      chapters: [],
      marketing: null,
      canon: {
        project: null,
        characters: [],
        locations: [],
        chapters: [],
        memories: [],
        cover: null,
        storyBible: null,
        worldBible: null,
        visualBible: null,
        humorDNA: null
      },
      validated: {},
      createdAt: T.now(),
      updatedAt: T.now()
    };
  };

  function seedState() {
    const projet = T.blankProject('Mon webtoon', '');
    return {
      version: 2,
      projects: [projet],
      activeId: projet.id,
      settings: { model: 'gemini-3.1-flash-lite-preview', chapters: 1 }
    };
  }

  T.state = null;

  T.load = function () {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (!raw) { T.state = seedState(); T.save(); return; }
      const parsed = JSON.parse(raw);
      if (!parsed || !Array.isArray(parsed.projects) || !parsed.projects.length) throw new Error('état invalide');
      T.state = parsed;
      T.state.settings = Object.assign({ model: 'gemini-3.1-flash-lite-preview', chapters: 1 }, parsed.settings || {});
      // Plus de mode démo ni de clé stockée dans le navigateur.
      delete T.state.settings.demo;
      delete T.state.settings.apiKey;
      // Retire le projet de démonstration des versions précédentes s'il est resté vierge.
      T.state.projects = T.state.projects.filter((pr) => !(pr.title === 'Projet démo ToonIA' && !pr.analysis && !(pr.chapters || []).length));
      if (!T.state.projects.length) T.state.projects.push(T.blankProject('Mon webtoon', ''));
      T.state.version = 2;
      if (!T.state.projects.some((p) => p.id === T.state.activeId)) T.state.activeId = T.state.projects[0].id;
    } catch (e) {
      T.state = seedState();
      try { T.save(); } catch (_) {}
    }
  };

  T.save = function () {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(T.state));
      return true;
    } catch (e) {
      T.toast('Sauvegarde impossible : stockage local plein ou bloqué.', 'error');
      return false;
    }
  };

  T.saveQuiet = function () {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(T.state)); } catch (_) {}
  };

  T.project = function () {
    if (!T.state) return null;
    return T.state.projects.find((p) => p.id === T.state.activeId) || T.state.projects[0] || null;
  };

  T.update = function (fn, opts) {
    const p = T.project();
    if (!p) return;
    fn(p);
    p.updatedAt = T.now();
    if (!p.title) p.title = 'Nouveau webtoon';
    T.save();
    if (!opts || !opts.silent) T.emit();
  };

  const listeners = [];
  T.onChange = (fn) => listeners.push(fn);
  T.emit = () => listeners.forEach((fn) => { try { fn(); } catch (_) {} });

  T.toast = function (msg, kind) {
    const wrap = document.getElementById('toasts');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'toast' + (kind === 'error' ? ' is-error' : kind === 'ok' ? ' is-ok' : '');
    const ico = document.createElement('span');
    ico.className = 't-ico';
    ico.textContent = kind === 'error' ? '⚠' : kind === 'ok' ? '✓' : '✦';
    const txt = document.createElement('span');
    txt.textContent = msg;
    el.appendChild(ico);
    el.appendChild(txt);
    wrap.appendChild(el);
    while (wrap.children.length > 3) wrap.removeChild(wrap.firstChild);
    setTimeout(() => { el.style.transition = 'opacity .25s'; el.style.opacity = '0'; }, 3400);
    setTimeout(() => el.remove(), 3800);
  };

  /* Amorces de brief proposées sur l'accueil : texte libre, modifiable ensuite. */
  T.briefPresets = [
    {
      id: 'thriller',
      label: 'Thriller portuaire',
      title: 'Les Fissures du quai',
      idea: 'Thriller psychologique dans une ville portuaire française de 1990. Sujet : une inspectrice en disgrâce, revenue après dix ans d’exil, enquête sur trois disparitions que sa hiérarchie veut enterrer. Elle reconnaît sur une photo un homme qu’elle croyait mort. Ton : pluie, néons, culpabilité, dialogues secs. Personnages : l’inspectrice obstinée, un journaliste idéaliste, un docker au passé trouble. Ambiance : polar âpre, cliffhangers en fin de chapitre. Fin : la vérité coûte sa carrière à l’héroïne. 3 chapitres, palette bleu acier et sodium.'
    },
    {
      id: 'fantasy',
      label: 'Fantasy sombre',
      title: 'La Bibliothèque des oublis',
      idea: 'Fantasy sombre dans un empire où la magie se paie en souvenirs. Sujet : une archiviste découvre qu’elle a vendu son enfance pour sauver sa sœur, devenue impératrice. Ton : mélancolique et grandiose. Personnages : l’archiviste, la sœur impératrice, un garde qui collectionne les souvenirs volés. Ambiance : bibliothèques immenses, cérémonies, neige. Contraintes : pas de voyage temporel. Arc : récupérer sa mémoire sans détruire l’empire. 2 chapitres, palette violet profond, or et blanc.'
    },
    {
      id: 'romance',
      label: 'Romance douce-amère',
      title: 'Service de nuit',
      idea: 'Romance douce-amère dans un petit restaurant de quartier menacé de fermeture. Sujet : deux anciens amis d’enfance se retrouvent, l’un cuisinier, l’autre critique gastronomique envoyée pour l’évaluer. Ton : chaleureux, drôle, quelques larmes. Personnages : le cuisinier taciturne, la critique brillante et blessée, la grand-mère qui tient les comptes. Ambiance : cuisine la nuit, marchés, pluie sur les vitres. Contraintes : pas de triangle amoureux. Fin : le restaurant sauvé, la relation à reconstruire. 2 chapitres, palette crème, rouge tomate, vert basilic.'
    }
  ];

  /* ---------------- client Gemini (proxy serveur) ---------------- */
  const AI = (T.ai = {});

  AI.MODELS = [
    'gemini-3.1-flash-lite-preview',
    'gemini-3.5-flash',
    'gemini-3.8-flash',
    'gemini-3.1-pro-preview'
  ];
  AI.server = { checked: false, hasKey: false, model: null };
  AI.hasServerKey = () => !!(AI.server && AI.server.hasKey);

  /**
   * Interroge /api/config : le proxy lit la variable TOON_IA côté serveur.
   * Aucune clé n'est jamais manipulée par le navigateur.
   */
  AI.checkServer = async function () {
    try {
      const res = await fetch('/api/config', { cache: 'no-store' });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const cfg = await res.json();
      AI.server = { checked: true, hasKey: !!cfg.hasServerKey, model: cfg.model || null };
      // Ne remplace le choix de l'utilisateur que s'il n'a encore aucun modèle enregistré.
      if (cfg.model && !T.state.settings.model) T.state.settings.model = cfg.model;
    } catch (_) {
      AI.server = { checked: true, hasKey: false, model: null };
    }
    return AI.server;
  };

  AI.engineLabel = function () {
    if (AI.hasServerKey()) return 'Gemini · TOON_IA';
    if (!AI.server.checked) return 'Gemini · vérification…';
    return 'Moteur non configuré';
  };

  const NO_KEY_MESSAGE =
    'La variable TOON_IA est absente de ce déploiement. Ajoutez-la dans Vercel → Settings → Environment Variables (Production), puis redéployez. La clé reste côté serveur.';

  function extractJson(data) {
    const cand = data && data.candidates && data.candidates[0];
    const text = cand && cand.content && cand.content.parts ? cand.content.parts.map((x) => x.text || '').join('') : '';
    if (!text) throw new Error('Réponse vide de Gemini (filtre de sécurité ou quota).');
    try {
      return JSON.parse(text);
    } catch (e) {
      throw new Error('Réponse JSON invalide de Gemini.');
    }
  }

  function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

  async function callGeminiOnce(prompt, schema, temperature) {
    const model = T.state.settings.model || AI.server.model || 'gemini-3.1-flash-lite-preview';
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 90000);
    let res;

    try {
      res = await fetch('/api/gemini', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: ctrl.signal,
        body: JSON.stringify({ prompt, schema, temperature: typeof temperature === 'number' ? temperature : 0.9, model })
      });
    } catch (e) {
      clearTimeout(timer);
      if (e.name === 'AbortError') throw new Error('Délai dépassé (90 s) : Gemini n’a pas répondu.');
      throw new Error('Réseau indisponible ou requête bloquée. ' + (e.message || ''));
    }
    clearTimeout(timer);

    const data = await res.json().catch(() => null);
    if (!res.ok) {
      if (res.status === 404) {
        throw new Error('Proxy /api/gemini introuvable. Ouvrez le site déployé sur Vercel : la clé TOON_IA y est lue côté serveur.');
      }
      const msg = data && data.error && data.error.message ? data.error.message : 'HTTP ' + res.status;
      const err = new Error(msg);
      err.status = res.status;
      throw err;
    }
    return extractJson(data);
  }

  // La génération complète enchaîne une vingtaine d'appels : le quota Gemini
  // est limité par minute, on réessaie donc avec une attente croissante.
  const RETRY_DELAYS = [0, 15000, 30000, 45000];

  async function callGemini(prompt, schema, temperature) {
    if (AI.server.checked && !AI.hasServerKey()) throw new Error(NO_KEY_MESSAGE);
    let last = null;
    for (let i = 0; i < RETRY_DELAYS.length; i++) {
      if (RETRY_DELAYS[i]) await sleep(RETRY_DELAYS[i]);
      try {
        return await callGeminiOnce(prompt, schema, temperature);
      } catch (e) {
        last = e;
        const retryable = e.status === 429 || e.status === 503 || (e.status >= 500 && e.status !== 504);
        if (!retryable) throw e;
      }
    }
    if (last && last.status === 429) {
      throw new Error('Quota Gemini atteint (limite de requêtes par minute du projet). Attendez une minute puis relancez : le projet reprend à l’étape en échec.');
    }
    throw last;
  }
  T.callGemini = callGemini;

  function canonContext(p) {
    const c = p.canon || {};
    const facts = (c.memories || []).map((m) => '- [' + m.category + '] ' + m.fact).join('\n') || '- (aucune mémoire canonique)';
    const chars = (c.characters || []).map((x) => '- ' + x.name + ' (' + x.role + ') : ' + (x.physicalAppearance || '') + ' | ' + (x.personality || '')).join('\n') || '- (aucun personnage validé)';
    const locs = (c.locations || []).map((l) => '- ' + l.name + ' : ' + l.description + ' | ' + (l.atmosphere || '')).join('\n') || '- (aucun lieu validé)';
    return 'CANON VALIDÉ (ne jamais contredire, ne jamais corriger en silence) :\nPersonnages :\n' + chars + '\nLieux :\n' + locs + '\nFaits :\n' + facts;
  }

  const S = {
    str: { type: 'STRING' },
    list: { type: 'ARRAY', items: { type: 'STRING' } }
  };

  const SCHEMAS = {
    analysis: {
      type: 'OBJECT',
      properties: {
        genrePrincipal: S.str, sousGenres: S.list, ton: S.str, themesDetectes: S.list,
        elementsNarratifsExplicites: S.list, ambiguitesAClarifier: S.list
      },
      required: ['genrePrincipal', 'sousGenres', 'ton', 'themesDetectes', 'elementsNarratifsExplicites', 'ambiguitesAClarifier']
    },
    characters: {
      type: 'OBJECT',
      properties: {
        characters: {
          type: 'ARRAY',
          items: {
            type: 'OBJECT',
            properties: {
              name: S.str, role: S.str, archetype: S.str, personality: S.str, physicalAppearance: S.str,
              distinctiveFeatures: S.str, characteristicClothing: S.str, abilities: S.str, relationships: S.str,
              plannedArc: S.str, currentState: S.str
            },
            required: ['name', 'role', 'archetype', 'personality', 'physicalAppearance', 'distinctiveFeatures', 'characteristicClothing', 'abilities', 'relationships', 'plannedArc', 'currentState']
          }
        }
      },
      required: ['characters']
    },
    world: {
      type: 'OBJECT',
      properties: {
        rules: S.str, factions: S.str, geography: S.str, socialStructure: S.str, historyLore: S.str,
        terminology: S.str, worldConstraints: S.str,
        locations: { type: 'ARRAY', items: { type: 'OBJECT', properties: { name: S.str, description: S.str, atmosphere: S.str }, required: ['name', 'description', 'atmosphere'] } },
        objects: { type: 'ARRAY', items: { type: 'OBJECT', properties: { name: S.str, significance: S.str, description: S.str }, required: ['name', 'significance', 'description'] } }
      },
      required: ['rules', 'factions', 'geography', 'socialStructure', 'historyLore', 'terminology', 'worldConstraints', 'locations', 'objects']
    },
    story: {
      type: 'OBJECT',
      properties: {
        premise: S.str, confirmedGenre: S.str, mainThemes: S.str, generalTone: S.str, mainConflicts: S.str,
        mainPlot: S.str, subPlots: S.str, narrativeDirection: S.str, pacing: S.str,
        timelineEvents: { type: 'ARRAY', items: { type: 'OBJECT', properties: { title: S.str, description: S.str, dateOrEpoch: S.str }, required: ['title', 'description', 'dateOrEpoch'] } }
      },
      required: ['premise', 'confirmedGenre', 'mainThemes', 'generalTone', 'mainConflicts', 'mainPlot', 'subPlots', 'narrativeDirection', 'pacing', 'timelineEvents']
    },
    visualHumor: {
      type: 'OBJECT',
      properties: {
        visual: { type: 'OBJECT', properties: { artStyle: S.str, colorPalette: S.str, panelingStyle: S.str, lightingMood: S.str, recurringVisualElements: S.str, framingRules: S.str, continuityElements: S.str }, required: ['artStyle', 'colorPalette', 'panelingStyle', 'lightingMood', 'recurringVisualElements', 'framingRules', 'continuityElements'] },
        humor: { type: 'OBJECT', properties: { humorStyle: S.str, chibiMomentsAllowed: { type: 'BOOLEAN' }, runningGags: S.str, punchlineFrequency: S.str, characterHumorHooks: S.str, comicSituations: S.str }, required: ['humorStyle', 'chibiMomentsAllowed', 'runningGags', 'punchlineFrequency', 'characterHumorHooks', 'comicSituations'] }
      },
      required: ['visual', 'humor']
    },
    chapter: {
      type: 'OBJECT',
      properties: {
        title: S.str, synopsis: S.str,
        scenes: {
          type: 'ARRAY',
          items: {
            type: 'OBJECT',
            properties: {
              title: S.str, locationName: S.str, charactersPresent: S.list, summary: S.str, beats: S.list,
              atmosphere: S.str, emotionalArc: S.str, pacingNotes: S.str
            },
            required: ['title', 'locationName', 'charactersPresent', 'summary', 'beats', 'atmosphere', 'emotionalArc', 'pacingNotes']
          }
        }
      },
      required: ['title', 'synopsis', 'scenes']
    },
    sceneDetail: {
      type: 'OBJECT',
      properties: {
        enrichedSummary: S.str, pacingNotes: S.str, emotionalArc: S.str,
        detailedNarrativeBeats: S.list, atmosphereAndSensory: S.str, characterInteractions: S.str
      },
      required: ['enrichedSummary', 'pacingNotes', 'emotionalArc', 'detailedNarrativeBeats', 'atmosphereAndSensory', 'characterInteractions']
    },
    panels: {
      type: 'OBJECT',
      properties: {
        panels: {
          type: 'ARRAY',
          items: {
            type: 'OBJECT',
            properties: {
              charactersPresent: S.str, location: S.str, actionDescription: S.str, dominantEmotion: S.str,
              cameraAngle: S.str, composition: S.str, lightingAndAtmosphere: S.str, backgroundDetails: S.str,
              visualEffects: S.str, visualPrompt: S.str, continuityNotes: S.str, soundEffects: S.str
            },
            required: ['charactersPresent', 'location', 'actionDescription', 'dominantEmotion', 'cameraAngle', 'composition', 'lightingAndAtmosphere', 'backgroundDetails', 'visualEffects', 'visualPrompt', 'continuityNotes']
          }
        }
      },
      required: ['panels']
    },
    dialogues: {
      type: 'OBJECT',
      properties: {
        dialogues: {
          type: 'ARRAY',
          items: {
            type: 'OBJECT',
            properties: {
              speakerName: S.str, type: { type: 'STRING', enum: ['SPEECH', 'THOUGHT', 'SHOUT', 'WHISPER', 'NARRATION'] },
              emotion: S.str, placement: { type: 'STRING', enum: ['TOP_LEFT', 'TOP_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_RIGHT', 'CENTER'] },
              text: S.str, soundEffect: S.str
            },
            required: ['speakerName', 'type', 'emotion', 'placement', 'text']
          }
        }
      },
      required: ['dialogues']
    },
    marketing: {
      type: 'OBJECT',
      properties: {
        titleProposals: S.list,
        synopsisVariants: { type: 'ARRAY', items: { type: 'OBJECT', properties: { lengthType: S.str, text: S.str, tagline: S.str }, required: ['lengthType', 'text', 'tagline'] } },
        coverProposal: { type: 'OBJECT', properties: { composition: S.str, charactersPresent: S.list, atmosphereAndLighting: S.str, colorPalette: S.str, detailedDescriptionNoText: S.str }, required: ['composition', 'charactersPresent', 'atmosphereAndLighting', 'colorPalette', 'detailedDescriptionNoText'] }
      },
      required: ['titleProposals', 'synopsisVariants', 'coverProposal']
    }
  };

  function projectBrief(p) {
    const a = p.analysis || {};
    return 'PROJET : ' + p.title + '\nBRIEF DE L’AUTEUR :\n' + p.idea + '\nGENRE : ' + (a.genrePrincipal || '') + '\nTON : ' + (a.ton || '') +
      '\nTHÈMES : ' + ((a.themesDetectes || []).join(', ')) + '\n' + canonContext(p);
  }

  AI.analyze = async function (p) {
    const adv = p.advanced || {};
    const advTxt = Object.keys(adv).filter((k) => adv[k]).map((k) => k + '=' + adv[k]).join('\n') || '(aucun)';
    const prompt = 'Tu es analyste narratif pour un studio de webtoon. Analyse le brief et renvoie un JSON strict.\n\nTITRE : ' + p.title +
      '\nBRIEF :\n' + p.idea + '\n\nDÉTAILS AVANCÉS :\n' + advTxt +
      '\n\nRÈGLES :\n- genrePrincipal : un genre principal court.\n- sousGenres : 2 à 4 sous-genres.\n- ton : description courte du ton narratif.\n- themesDetectes : 3 à 5 thèmes présents dans le brief.\n- elementsNarratifsExplicites : ce que l’auteur a explicitement posé (repris fidèlement, sans invention).\n- ambiguitesAClarifier : 2 à 5 questions précises à poser à l’auteur. Ne pas inventer de réponse.\n- Ne corrige pas le brief, ne complète pas les vides.\n- Réponds en français.';
    const out = await callGemini(prompt, SCHEMAS.analysis, 0.6);
    if (!out || !out.genrePrincipal) throw new Error('Analyse incomplète renvoyée par Gemini.');
    return Object.assign({}, out, { _genreId: T.profile(p).id });
  };

  AI.characters = async function (p) {
    const prompt = projectBrief(p) + '\n\nTÂCHE : construis la Character Bible (3 à 5 personnages) en respectant strictement le canon validé et les personnages déjà nommés dans le brief. Chaque personnage doit être visuellement et psychologiquement distinct. Décris l’apparence physique avec les couleurs de cheveux et d’yeux (indispensable pour la continuité). Réponds en français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.characters, 0.9);
    if (!out || !Array.isArray(out.characters) || !out.characters.length) throw new Error('Aucun personnage renvoyé par Gemini.');
    return out.characters.map((c) => Object.assign({}, c, { id: T.uid('char'), isValidated: false }));
  };

  AI.world = async function (p) {
    const prompt = projectBrief(p) + '\n\nTÂCHE : construis la World Bible. Règles du monde contraignantes et applicables, factions, géographie, structure sociale, lore, terminologie (liste à puces), contraintes. Puis 3 lieux et 2 objets narratifs liés aux personnages. Réponds en français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.world, 0.9);
    if (!out || !out.rules) throw new Error('World Bible incomplète renvoyée par Gemini.');
    return Object.assign({}, out, {
      locations: (out.locations || []).map((l, i) => Object.assign({}, l, { id: T.uid('loc'), orderIndex: i, isValidated: false })),
      objects: (out.objects || []).map((o) => Object.assign({}, o, { id: T.uid('obj'), ownerCharacterId: null, isValidated: false })),
      isValidated: false
    });
  };

  AI.story = async function (p) {
    const prompt = projectBrief(p) + '\n\nTÂCHE : construis la Story Bible. Prémisse fidèle au brief, thèmes, ton, conflits (interne/relationnel/sociétal), intrigue principale en 3 actes, sous-intrigues, climax et promesse finale, rythme. Puis 3 événements fondateurs antérieurs au chapitre 1. Ne contredis aucun fait canonique. Réponds en français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.story, 0.9);
    if (!out || !out.mainPlot) throw new Error('Story Bible incomplète renvoyée par Gemini.');
    return Object.assign({}, out, {
      timelineEvents: (out.timelineEvents || []).map((e, i) => Object.assign({}, e, { id: T.uid('evt'), orderIndex: i + 1, isValidated: false })),
      isValidated: false
    });
  };

  AI.visualHumor = async function (p) {
    const prompt = projectBrief(p) + '\n\nTÂCHE : définis la direction artistique et l’ADN humoristique. Le style doit être compatible avec un webtoon à défilement vertical. L’humour doit rester cohérent avec le ton (si le récit est tragique, pas de burlesque gratuit). Réponds en français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.visualHumor, 0.9);
    if (!out || !out.visual || !out.humor) throw new Error('Direction artistique incomplète renvoyée par Gemini.');
    return {
      visual: Object.assign({}, out.visual, { isValidated: false }),
      humor: Object.assign({}, out.humor, { isValidated: false })
    };
  };

  AI.chapter = async function (p, number) {
    const prompt = projectBrief(p) + '\n\nCHAPITRES DÉJÀ ÉCRITS : ' + ((p.chapters || []).length ? (p.chapters || []).map((c) => c.number + '. ' + c.title).join(' / ') : '(aucun)') +
      '\n\nTÂCHE : écris le script du chapitre ' + number + '. 3 à 5 scènes, chacune avec lieu canonique, personnages présents, résumé d’action et 3 à 5 beats narratifs. Pas encore de découpage en panels. Le chapitre doit faire avancer l’intrigue principale sans contredire le canon. Réponds en français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.chapter, 0.95);
    if (!out || !Array.isArray(out.scenes) || !out.scenes.length) throw new Error('Chapitre vide renvoyé par Gemini.');
    return {
      id: T.uid('chap'),
      number,
      title: out.title || 'Chapitre ' + number,
      synopsis: out.synopsis || '',
      isCanon: false,
      scenes: out.scenes.map((s, i) => ({
        id: T.uid('scene'), orderIndex: i + 1, title: s.title || 'Scène ' + (i + 1),
        locationName: s.locationName || '', charactersPresent: s.charactersPresent || [],
        summary: s.summary || '', beats: s.beats || [], atmosphere: s.atmosphere || '',
        emotionalArc: s.emotionalArc || '', pacingNotes: s.pacingNotes || '',
        detail: null, panels: [], isValidated: false
      }))
    };
  };

  AI.sceneDetail = async function (p, scene) {
    const prompt = projectBrief(p) + '\n\nSCÈNE À ENRICHIR :\nTitre : ' + scene.title + '\nLieu : ' + scene.locationName +
      '\nPersonnages : ' + (scene.charactersPresent || []).join(', ') + '\nRésumé : ' + scene.summary +
      '\nBeats : ' + (scene.beats || []).join(' | ') +
      '\n\nTÂCHE : enrichis la mise en scène sans réécrire l’histoire : résumé précisé, rythme, arc émotionnel, 4 à 6 micro-beats, ambiance sensorielle, interactions physiques entre personnages. Réponds en français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.sceneDetail, 0.9);
    if (!out || !out.enrichedSummary) throw new Error('Enrichissement incomplet renvoyé par Gemini.');
    return out;
  };

  AI.panels = async function (p, scene) {
    const prompt = projectBrief(p) + '\n\nSCÈNE À DÉCOUPER :\nTitre : ' + scene.title + '\nLieu : ' + scene.locationName +
      '\nPersonnages : ' + (scene.charactersPresent || []).join(', ') + '\nRésumé : ' + scene.summary +
      '\nBeats : ' + (scene.beats || []).join(' | ') +
      (scene.detail ? '\nMise en scène : ' + scene.detail.enrichedSummary + ' | ' + scene.detail.emotionalArc : '') +
      '\n\nTÂCHE : découpe cette scène en 3 à 6 panels de webtoon vertical. Pour chaque panel : personnages présents avec leur état, lieu, action, émotion dominante, angle de caméra, composition, éclairage/ambiance, arrière-plan, effets visuels, prompt visuel autonome (EN ANGLAIS, SANS TEXTE dans l’image), notes de continuité, onomatopée éventuelle. Français pour les descriptions. JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.panels, 0.95);
    if (!out || !Array.isArray(out.panels) || !out.panels.length) throw new Error('Aucun panel renvoyé par Gemini.');
    return out.panels.map((pn, i) => Object.assign({}, pn, { id: T.uid('panel'), orderIndex: i + 1, artSeed: scene.id + ':' + i, isValidated: false }));
  };

  AI.dialogues = async function (p, panel) {
    const prompt = projectBrief(p) + '\n\nPANEL :\nLieu : ' + panel.location + '\nPersonnages : ' + panel.charactersPresent +
      '\nAction : ' + panel.actionDescription + '\nÉmotion : ' + panel.dominantEmotion +
      '\n\nTÂCHE : écris 2 à 4 bulles pour ce panel. Types autorisés : SPEECH, THOUGHT, SHOUT, WHISPER, NARRATION. Placements : TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER. Textes courts (max 140 caractères), naturels, cohérents avec le canon. Réponds en français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.dialogues, 0.95);
    if (!out || !Array.isArray(out.dialogues) || !out.dialogues.length) throw new Error('Aucune bulle renvoyée par Gemini.');
    return out.dialogues.map((d) => Object.assign({}, d, { id: T.uid('dlg') }));
  };

  AI.marketing = async function (p) {
    const prompt = projectBrief(p) + '\n\nTÂCHE : packaging éditorial. 3 à 5 propositions de titre (courtes, mémorables). 3 synopsis (Court 1-2 phrases, Moyen 1 paragraphe, Long 2 paragraphes) avec une tagline pour chacun. Une description de couverture détaillée SANS AUCUN TEXTE dans l’image (composition, personnages, ambiance, palette), le titre sera superposé par l’application. Ne contredis pas le canon. Réponds en français, JSON strict.';
    const out = await callGemini(prompt, SCHEMAS.marketing, 1.0);
    if (!out || !Array.isArray(out.titleProposals) || !out.titleProposals.length) throw new Error('Packaging incomplet renvoyé par Gemini.');
    const synopsis = out.synopsisVariants || [];
    return Object.assign({}, out, {
      selectedTitle: out.titleProposals[0],
      selectedSynopsis: (synopsis[1] && synopsis[1].text) || (synopsis[0] && synopsis[0].text) || p.idea,
      selectedTagline: (synopsis[0] && synopsis[0].tagline) || '',
      selectedCoverDescription: (out.coverProposal && out.coverProposal.detailedDescriptionNoText) || '',
      isValidated: false
    });
  };

  AI.testKey = async function () {
    const res = await fetch('/api/gemini', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'Réponds uniquement : OK', temperature: 0 })
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error((data && data.error && data.error.message) || 'HTTP ' + res.status);
    return true;
  };

  /* ---------------- visuels procéduraux (SVG) ---------------- */
  const PALETTES = {
    cyberpunk: ['#6d4bd8', '#f0428b', '#22d3ee', '#08060f'],
    fantasy: ['#2f7d5a', '#e3c27a', '#1b3b5a', '#0d1220'],
    romance: ['#f6b7c9', '#e8d5b7', '#8ab6d6', '#3b2a3a'],
    horreur: ['#8a1c2b', '#4d6b52', '#cfc7b8', '#0a0a0c'],
    action: ['#f97316', '#fbbf24', '#1e3a8a', '#0b0a12'],
    mystere: ['#2c4a63', '#d9b26a', '#8a95a0', '#0a0d12'],
    default: ['#6d4bd8', '#f0428b', '#22d3ee', '#0b0912']
  };

  function paletteFor(seed, genreId) {
    const base = PALETTES[genreId] || PALETTES.default;
    const rng = T.rngFor(seed + 'pal');
    return rng() > 0.62 ? base.slice().reverse() : base;
  }

  T.art = function (seed, genreId) {
    const rng = T.rngFor(seed || 'art');
    const [c1, c2, c3, ink] = paletteFor(seed, genreId);
    const horizon = 55 + Math.floor(rng() * 20);
    const sunX = 15 + Math.floor(rng() * 70);
    const sunY = 18 + Math.floor(rng() * 26);
    const sunR = 12 + Math.floor(rng() * 14);
    const skyline = [];
    let x = -5;
    while (x < 105) {
      const w = 4 + rng() * 10;
      const h = 8 + rng() * 34;
      skyline.push('<rect x="' + x.toFixed(1) + '" y="' + (horizon + 20 - h).toFixed(1) + '" width="' + w.toFixed(1) + '" height="' + (h + 22).toFixed(1) + '" fill="' + ink + '" opacity="' + (0.55 + rng() * 0.4).toFixed(2) + '"/>');
      x += w + 1 + rng() * 3;
    }
    const rays = Array.from({ length: 14 }, (_, i) => {
      const a = (i / 14) * Math.PI * 2;
      return '<line x1="' + (sunX + Math.cos(a) * (sunR + 2)).toFixed(1) + '" y1="' + (sunY + Math.sin(a) * (sunR + 2)).toFixed(1) + '" x2="' + (sunX + Math.cos(a) * 90).toFixed(1) + '" y2="' + (sunY + Math.sin(a) * 90).toFixed(1) + '" stroke="' + c3 + '" stroke-width="0.5" opacity="0.18"/>';
    }).join('');
    const figure = rng() > 0.35
      ? '<g transform="translate(' + (62 + rng() * 20).toFixed(1) + ',' + (horizon + 2).toFixed(1) + ') scale(' + (0.85 + rng() * 0.5).toFixed(2) + ')">' +
        '<ellipse cx="0" cy="0" rx="5.4" ry="1.2" fill="#000" opacity="0.55"/>' +
        '<path d="M-3.4 -1 L-2.6 -14 Q0 -16 2.6 -14 L3.4 -1 Z" fill="' + ink + '"/>' +
        '<circle cx="0" cy="-17.4" r="3.1" fill="' + ink + '"/>' +
        '<path d="M-2.4 -17 Q0 -20.4 2.4 -17 Q2.8 -14.6 0 -13.8 Q-2.8 -14.6 -2.4 -17" fill="' + c1 + '" opacity="0.9"/>' +
        '</g>'
      : '';
    const svg =
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 100" preserveAspectRatio="xMidYMid slice">' +
      '<defs>' +
      '<linearGradient id="sky" x1="0" y1="0" x2="0.3" y2="1"><stop offset="0" stop-color="' + c1 + '"/><stop offset="0.55" stop-color="' + c2 + '" stop-opacity="0.75"/><stop offset="1" stop-color="' + ink + '"/></linearGradient>' +
      '<radialGradient id="glow"><stop offset="0" stop-color="' + c3 + '" stop-opacity="0.95"/><stop offset="1" stop-color="' + c3 + '" stop-opacity="0"/></radialGradient>' +
      '<pattern id="dots" width="4" height="4" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r="0.75" fill="#fff" opacity="0.16"/></pattern>' +
      '</defs>' +
      '<rect width="160" height="100" fill="url(#sky)"/>' +
      rays +
      '<circle cx="' + sunX + '" cy="' + sunY + '" r="' + (sunR * 2.6) + '" fill="url(#glow)"/>' +
      '<circle cx="' + sunX + '" cy="' + sunY + '" r="' + sunR + '" fill="' + c3 + '" opacity="0.85"/>' +
      '<rect y="' + horizon + '" width="160" height="' + (100 - horizon) + '" fill="' + ink + '" opacity="0.9"/>' +
      skyline.join('') +
      '<rect width="160" height="100" fill="url(#dots)"/>' +
      figure +
      '<rect x="0.6" y="0.6" width="158.8" height="98.8" fill="none" stroke="#000" stroke-width="1.2" opacity="0.55"/>' +
      '<rect width="160" height="100" fill="' + ink + '" opacity="0.06"/>' +
      '</svg>';
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  };

  /* ---------------- moteur canonique ---------------- */
  const Canon = (T.canon = {});

  const HAIR = ['blond', 'brun', 'noir', 'roux', 'blanc', 'argenté', 'bleu', 'vert', 'rose'];
  const EYES = ['bleu', 'vert', 'noir', 'marron', 'rouge', 'doré', 'ambre', 'violet'];

  function low(s) { return String(s || '').toLowerCase(); }

  function visualDivergence(canonDesc, newDesc) {
    const cd = low(canonDesc), nd = low(newDesc);
    if (!cd || !nd) return null;
    for (const color of HAIR) {
      if (cd.includes('cheveux ' + color) || cd.includes('chevelure ' + color)) {
        for (const other of HAIR) {
          if (other !== color && (nd.includes('cheveux ' + other) || nd.includes('chevelure ' + other))) {
            return 'Incompatibilité capillaire : cheveux ' + color + ' dans le canon, mais décrits avec des cheveux ' + other + ' dans le nouvel élément.';
          }
        }
      }
    }
    for (const color of EYES) {
      if (cd.includes('yeux ' + color)) {
        for (const other of EYES) {
          if (other !== color && nd.includes('yeux ' + other)) {
            return 'Incompatibilité oculaire : yeux ' + color + ' dans le canon, mais décrits avec des yeux ' + other + ' dans le nouvel élément.';
          }
        }
      }
    }
    return null;
  }
  Canon.visualDivergence = visualDivergence;

  function C(targetType, targetName, fieldName, canonicalValue, conflictingValue, description, severity) {
    return { id: T.uid('crt'), targetType, targetName, fieldName, canonicalValue, conflictingValue, description, severity };
  }

  Canon.detectCharacter = function (char, p) {
    const out = [];
    const c = p.canon || {};
    const existing = (c.characters || []).find((x) => low(x.name).trim() === low(char.name).trim() || x.id === char.id);
    if (existing) {
      if (existing.role && char.role && low(existing.role) !== low(char.role)) {
        out.push(C('Personnage', existing.name, 'Rôle', existing.role, char.role, 'Le rôle de ' + existing.name + ' dans le canon est « ' + existing.role + ' », mais le nouvel élément le définit comme « ' + char.role + ' ».', 'HIGH'));
      }
      if (existing.archetype && char.archetype && low(existing.archetype) !== low(char.archetype)) {
        out.push(C('Personnage', existing.name, 'Archétype', existing.archetype, char.archetype, 'L’archétype établi est « ' + existing.archetype + ' », différent de « ' + char.archetype + ' ».', 'MEDIUM'));
      }
      const vd = visualDivergence(existing.physicalAppearance, char.physicalAppearance);
      if (vd) out.push(C('Personnage', existing.name, 'Apparence visuelle', existing.physicalAppearance, char.physicalAppearance, vd, 'HIGH'));
    }
    const death = (c.memories || []).find((m) => low(m.fact).includes(low(char.name)) && /\b(mort|morte|meurt|mourir|décédé|décédée|décès|tué|tuée)\b/.test(low(m.fact)));
    if (death && !low(char.personality).includes('résurrection') && !low(char.plannedArc).includes('résurrection')) {
      out.push(C('Personnage', char.name, 'Statut vital (canon)', death.fact, 'Personnage réintroduit actif', 'Une mémoire canonique établit que « ' + death.fact + ' ». La réintroduction de ' + char.name + ' contredit ce fait absolu.', 'HIGH'));
    }
    return out;
  };

  Canon.detectLocation = function (loc, p) {
    const out = [];
    const existing = ((p.canon || {}).locations || []).find((x) => low(x.name).trim() === low(loc.name).trim());
    if (existing && existing.atmosphere && loc.atmosphere && low(existing.atmosphere) !== low(loc.atmosphere)) {
      out.push(C('Lieu', existing.name, 'Atmosphère', existing.atmosphere, loc.atmosphere, 'L’atmosphère canonique de ' + existing.name + ' est « ' + existing.atmosphere + ' », modifiée en « ' + loc.atmosphere + ' ».', 'MEDIUM'));
    }
    return out;
  };

  Canon.detectChapter = function (chapter, p) {
    const dup = ((p.canon || {}).chapters || []).find((x) => Number(x.number) === Number(chapter.number) && x.id !== chapter.id);
    if (dup) {
      return [C('Chapitre', 'Chapitre ' + chapter.number, 'Numéro', 'Chapitre existant : « ' + dup.title + ' »', 'Nouveau chapitre : « ' + chapter.title + ' »', 'Le numéro de chapitre ' + chapter.number + ' est déjà attribué au chapitre canonique « ' + dup.title + ' ».', 'HIGH')];
    }
    return [];
  };

  Canon.detectMemory = function (memory, p) {
    const existing = ((p.canon || {}).memories || []).find((m) => low(m.category) === low(memory.category) && low(m.fact).includes(low(String(memory.fact).slice(0, 20))) && m.id !== memory.id);
    if (existing && existing.fact !== memory.fact) {
      return [C('Mémoire canonique', memory.category, 'Fait établi', existing.fact, memory.fact, 'Un fait canonique similaire existe déjà avec une formulation différente.', 'MEDIUM')];
    }
    return [];
  };

  Canon.detectCover = function (cover, p) {
    const out = [];
    const desc = low(cover.visualDescription);
    const banned = ['avec le titre', 'titre écrit', 'texte écrit', 'logo incrusté', 'lettres dorées', 'typographie sur l’image', 'typographie sur l\'image', 'texte intégré', 'titre en gros'];
    for (const kw of banned) {
      if (desc.includes(kw)) {
        out.push(C('Couverture', cover.title || 'Couverture', 'Interdiction de texte intégré', 'Génération sans aucun texte (le texte est superposé nativement)', kw, 'La description de couverture demande « ' + kw + ' ». Règle absolue ToonIA : les illustrations sont générées SANS texte ni logo. Tout texte est rendu nativement par l’application.', 'HIGH'));
        break;
      }
    }
    for (const char of ((p.canon || {}).characters || [])) {
      if (desc.includes(low(char.name))) {
        const vd = visualDivergence(char.physicalAppearance, cover.visualDescription);
        if (vd) out.push(C('Couverture', char.name, 'Continuité visuelle du personnage', char.physicalAppearance, cover.visualDescription, 'Dans la couverture : ' + vd, 'MEDIUM'));
      }
    }
    return out;
  };

  Canon.detectSynopsis = function (title, synopsis, p) {
    const out = [];
    if (!synopsis) return out;
    const syn = low(synopsis);
    const sb = (p.canon || {}).storyBible;
    if (sb) {
      const grim = low(sb.mainThemes).includes('sombre') || low(sb.mainThemes).includes('tragique') || low(sb.narrativeDirection).includes('tragique');
      const comedy = syn.includes('comédie insouciante') || syn.includes('gags burlesques') || syn.includes('histoire légère et sans danger');
      if (grim && comedy) {
        out.push(C('Synopsis', title || 'Synopsis final', 'Harmonie tonale', 'Story Bible sombre/tragique : ' + sb.mainThemes, synopsis, 'Le synopsis présente une intrigue purement comique ou légère en contradiction directe avec les enjeux tragiques validés dans la Story Bible.', 'HIGH'));
      }
    }
    for (const char of ((p.canon || {}).characters || [])) {
      if (!syn.includes(low(char.name))) continue;
      const dead = low(char.personality).includes('mort') || low(char.personality).includes('décédé');
      if (dead && (syn.includes(low(char.name) + ' part') || syn.includes(low(char.name) + ' explore') || syn.includes(low(char.name) + ' combat aux côtés'))) {
        out.push(C('Synopsis', char.name, 'Statut vital', char.name + ' est canoniquement décédé', 'Décrit en action dans le synopsis', 'Le personnage ' + char.name + ' est validé comme décédé dans le canon, mais le synopsis le présente comme un protagoniste actif.', 'HIGH'));
      }
      if (low(char.role) === 'protagoniste' && (syn.includes('antagoniste ' + low(char.name)) || syn.includes('le traître ' + low(char.name)) || syn.includes(low(char.name) + ', l’ennemi juré'))) {
        out.push(C('Synopsis', char.name, 'Rôle canonique', 'Protagoniste majeur (' + char.name + ')', 'Décrit comme antagoniste/traître', char.name + ' est le protagoniste du canon, mais le synopsis le qualifie d’antagoniste.', 'HIGH'));
      }
    }
    return out;
  };

  Canon.detect = function (kind, item, p) {
    if (kind === 'character') return Canon.detectCharacter(item, p);
    if (kind === 'location') return Canon.detectLocation(item, p);
    if (kind === 'chapter') return Canon.detectChapter(item, p);
    if (kind === 'memory') return Canon.detectMemory(item, p);
    if (kind === 'cover') return Canon.detectCover(item, p);
    return [];
  };

  Canon.counts = function (p) {
    const c = (p && p.canon) || {};
    return {
      characters: (c.characters || []).length,
      locations: (c.locations || []).length,
      chapters: (c.chapters || []).length,
      memories: (c.memories || []).length
    };
  };

  T.stats = function (p) {
    const panels = ((p.chapters || []).flatMap((ch) => ch.scenes || []).flatMap((s) => s.panels || []));
    return {
      characters: (p.characters || []).length,
      locations: ((p.world && p.world.locations) || []).length,
      chapters: (p.chapters || []).length,
      scenes: (p.chapters || []).flatMap((ch) => ch.scenes || []).length,
      panels: panels.length,
      dialogues: panels.flatMap((pn) => pn.dialogues || []).length
    };
  };

  T.progress = function (p) {
    return [
      { key: 'projet', label: 'Idée', done: !!p.analysis },
      { key: 'personnages', label: 'Personnages', done: !!(p.characters && p.characters.length) },
      { key: 'univers', label: 'Univers', done: !!p.world },
      { key: 'histoire', label: 'Histoire', done: !!p.story },
      { key: 'direction', label: 'Direction', done: !!(p.visual && p.humor) },
      { key: 'chapitre', label: 'Chapitres', done: !!(p.chapters && p.chapters.length) },
      { key: 'panels', label: 'Panels', done: !!(p.chapters || []).some((c) => (c.scenes || []).some((s) => (s.panels || []).length)) },
      { key: 'dialogues', label: 'Dialogues', done: !!(p.chapters || []).some((c) => (c.scenes || []).some((s) => (s.panels || []).some((pn) => (pn.dialogues || []).length))) },
      { key: 'couverture', label: 'Couverture', done: !!p.marketing }
    ];
  };
})();
