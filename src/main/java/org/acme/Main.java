package org.acme;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@QuarkusMain
public class Main implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        Quarkus.waitForExit();
        return 0;
    }

    @ServerEndpoint("/game")
    public static class GameWebSocket {

        private static final List<Session> sessions = new CopyOnWriteArrayList<>();
        private static final ConcurrentHashMap<Session, Integer> scores = new ConcurrentHashMap<>();
        private static final JeuDeFruits jeu = new JeuDeFruits();

        @OnOpen
        public void onOpen(Session session) {
            System.out.println("Session " + session.getId() + " has opened a connection");
            sessions.add(session);
            scores.put(session, 0);
            session.getAsyncRemote().sendText("Welcome to the Fruit Game!");
            startGameLoop();
        }

        @OnMessage
        public void onMessage(Session session, String message) {
            if ("Rejouer".equals(message)) {
                // Réinitialise le score du joueur
                scores.put(session, 0);
                // Réinitialise le jeu (cette méthode doit être définie dans JeuDeFruits)
                jeu.reinitialiserJeu();
                // Informe le client que le score a été réinitialisé
                session.getAsyncRemote().sendText("Score: 0");
            } else if (message.startsWith("RemoveScore,")) {
                // Traitement des clics sur les émojis crotte pour réduire le score
                String[] parts = message.substring(12).split(",");
                if (parts.length == 2) {
                    try {
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        // Si un fruit (ou crotte) est bien à cette position, on le supprime
                        if (jeu.destroyFruitAt(x, y)) {
                            // Décrémente le score car c'était une crotte
                            scores.merge(session, -1, Integer::sum);
                            session.getAsyncRemote().sendText("Score: " + scores.get(session));
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Received message not in expected format: 'RemoveScore,x,y'");
                    }
                }
            } else {
                // Traitement des clics sur les fruits pour augmenter le score
                String[] parts = message.split(",");
                if (parts.length == 2) {
                    try {
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        // Si un fruit est bien à cette position, on le supprime
                        if (jeu.destroyFruitAt(x, y)) {
                            // Incrémente le score car un fruit a été touché
                            scores.merge(session, 1, Integer::sum);
                            session.getAsyncRemote().sendText("Score: " + scores.get(session));
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Received message is not in the expected format: 'x,y'");
                    }
                }
            }
        }

        private static void startGameLoop() {
            new Thread(() -> {
                while (true) {
                    jeu.genererFruit();
                    jeu.deplacerFruits();
                    envoyerEtatFruits();
                    try {
                        Thread.sleep(700); // Mise à jour chaque seconde
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        private static void envoyerEtatFruits() {
            String fruitData = jeu.getFruitsData();
            for (Session session : sessions) {
                session.getAsyncRemote().sendText(fruitData);
            }
        }
    }

    public static class JeuDeFruits {
        private List<Fruit> fruits;
        private Random random;

        public JeuDeFruits() {
            fruits = new ArrayList<>();
            random = new Random();
        }

        public void reinitialiserJeu() {
            // Vide la liste des fruits pour repartir de zéro
            fruits.clear();
            // Réinitialiser d'autres états du jeu si nécessaire
        }

        public void genererFruit() {
            if (fruits.size() < 10) {
                int x = random.nextInt(1000);
                int y = 0;
                boolean isPoop = random.nextInt(5) < 2; // 40% de chance de générer une crotte
                Fruit fruit = new Fruit(x, y, isPoop);
                fruits.add(fruit);
                System.out.println("Generated " + (isPoop ? "poop" : "fruit") + " at x=" + x);
            }
        }




        public void deplacerFruits() {
            List<Fruit> fruitsASupprimer = new ArrayList<>();
            // Déplace chaque fruit vers le bas
            for (Fruit fruit : fruits) {
                fruit.y += 13; // Suppose que l'augmentation de y les fait descendre
                // Vérifie si le fruit doit être détruit car il est sorti de l'aire de jeu
                if (fruit.y > 500) { // Changez cette valeur selon la limite inférieure de votre aire de jeu
                    fruitsASupprimer.add(fruit);
                }
            }
            // Supprime tous les fruits qui ont atteint la condition y < 10
            fruits.removeAll(fruitsASupprimer);
        }


        public String getFruitsData() {
            StringBuilder builder = new StringBuilder();
            for (Fruit fruit : fruits) {
                builder.append(fruit.id).append(",").append(fruit.x).append(",").append(fruit.y).append(",").append(fruit.isPoop ? "💩" : "").append(";");
            }
            return builder.toString();
        }


        public boolean destroyFruitAt(int x, int y) {
            for (Fruit fruit : new ArrayList<>(fruits)) { // Crée une copie pour éviter les modifications concurrentes
                if (Math.abs(fruit.x - x) < 50 && Math.abs(fruit.y - y) < 50) { // Vérifie si le clic est proche d'un fruit
                    fruits.remove(fruit);
                    return true; // Un fruit a été détruit
                }

            }
            return false; // Aucun fruit n'a été détruit
        }

        private static class Fruit {
            static int nextId = 0; // Compteur pour les ID
            int id, x, y;
            boolean isPoop;

            Fruit(int x, int y, boolean isPoop) {
                this.id = nextId++; // Attribue un ID unique à chaque fruit
                this.x = x;
                this.y = y;
                this.isPoop = isPoop;
            }
        }

    }
}