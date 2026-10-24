# Cadence des barricades

La réparation est limitée côté serveur à 25 ticks par joueur (1,25 seconde à 20 TPS), ou 13 ticks avec Speed Cola (arrondi au tick supérieur). Pendant le maintien de la touche, le client continue les demandes ; une demande trop précoce ne coûte plus un nouvel intervalle complet. Seule une réparation effective consomme le délai. Les sons viennent de la réparation confirmée par le serveur.

Les deux battants liés conservent un état partagé. Le compteur d’attaque est maintenant partagé lui aussi, par zombie et par paire : un zombie au milieu retire un seul stage toutes les 60 ticks. Plusieurs zombies conservent chacun leur attaque.

Le scénario `tools/tests/BarricadeRegressionTest.java`, exécuté dans un serveur intégré réel par `LunarCorrectionsClientTest`, vérifie les demandes anticipées et dupliquées, les moitiés hautes, Speed Cola, les demandes sur une porte complète, les deux ordres de traitement des battants et deux zombies simultanés.
