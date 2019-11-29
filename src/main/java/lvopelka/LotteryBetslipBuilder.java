package lvopelka;

import org.apache.commons.math3.util.CombinatoricsUtils;

import java.text.DecimalFormat;
import java.util.*;

/**
 * This class demonstrates winning calculation for world lotteries
 * @author lukasvopelka
 */
public class WorldLotteriesBetBuilder {

    private static DecimalFormat DF_2 = new DecimalFormat("#.##");
    private static DecimalFormat DF_8 = new DecimalFormat("#.########");


    private static final LotterySystem LOTTERY_6_49 = new LotterySystem(49, 6, new double[]{7.75, 70.00, 700, 8400, 210000});

    /**
     * Run this for chosen lottery and player selection
     * @param args
     */
    public static void main(String[] args) {

        LotterySystem systems[] = {
                LOTTERY_6_49
        };

        PlayerMultiChoice singleChoices[] = new PlayerMultiChoice[]{

                new PlayerMultiChoice(
                        Arrays.asList(
                                new PlayerSingleChoice(1, 15),
                                new PlayerSingleChoice(2, 15),
                                new PlayerSingleChoice(3, 15)),


                        new int[]{10, 20, 30, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 1, 2}, 1d)
        };

        for (LotterySystem system : systems) {
            System.out.println(system);
            System.out.println(System.lineSeparator());
            for (PlayerMultiChoice playerChoice : singleChoices) {
                Betslip betslip = new Betslip.Builder()
                        .withLotterySystem(system)
                        .withPlayerChoice(playerChoice).build();
                System.out.println(playerChoice);
                System.out.println();
                System.out.println(betslip);
            }
        }

    }

    /**
     * Lottery system with configured odds
     */
    private static class LotterySystem {

        private final Combination combination;
        private final Map<Integer, Double> odds = new HashMap<>();

        public LotterySystem(int total, int selected, double[] configuredOdds) {
            this.combination = new Combination(selected, total);
            for (int i = 1; i <= configuredOdds.length; i++) {
                odds.put(Integer.valueOf(i), configuredOdds[i - 1]);
            }
        }

        public int getSelected() {
            return combination.getK();
        }

        public int getTotal() {
            return combination.getN();
        }

        public Combination getCombination() {
            return combination;
        }

        public double getGroupOdds(int group) {
            return odds.get(Integer.valueOf(group));
        }

        @Override
        public String toString() {
            return "Lottery: " + combination + ", odds " + odds;
        }
    }

    /**
     * Lottery betslip
     */
    private static final class Betslip {

        private final List<Hit> hits;

        public Betslip(List<Hit> hits) {
            this.hits = hits;
        }


        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("-----------------------------------------------------");
            builder.append(System.lineSeparator());
            builder.append("Hits:");
            builder.append(System.lineSeparator());
            builder.append("-----------------------------------------------------");
            builder.append(System.lineSeparator());
            for (Hit hit : this.hits) {
                builder.append(hit);
                builder.append(System.lineSeparator());
            }
            return builder.toString();
        }

        public static class Builder {
            private LotterySystem lotterySystem;
            private PlayerMultiChoice playerMultiChoice;
            private Map<Integer, List<WinningCombination>> winningCombinations = new LinkedHashMap<>();

            public Builder withLotterySystem(LotterySystem system) {
                this.lotterySystem = system;
                return this;
            }

            public Builder withPlayerChoice(PlayerMultiChoice choice) {
                this.playerMultiChoice = choice;
                return this;
            }

            public Betslip build() {
                List<Hit> hits = new ArrayList<>();
                // Calculate winning combination for each possible hit
                this.winningCombinations = calculateWinningCombinations();

                // Divide stake equally for each virtual ticket
                double stake = playerMultiChoice.getStake() / (double) this.playerMultiChoice.getTotalCombinationsCount();
                for (Integer hit : getHits()) {
                    double win = calculatePossibleWin(hit, stake);
                    hits.add(new Hit(lotterySystem, playerMultiChoice, hit, getCombinationsForHit(hit), stake, win));
                }
                return new Betslip(hits);
            }

            private final Map<Integer, List<WinningCombination>> calculateWinningCombinations() {
                Map<Integer, List<WinningCombination>> map = new LinkedHashMap<>();

                for (int hitNumber = playerMultiChoice.getMinGroup(); hitNumber <= Math.min(playerMultiChoice.getBullets(), lotterySystem.getSelected()); hitNumber++) {
                    map.put(Integer.valueOf(hitNumber), new ArrayList<>());
                    for (PlayerSingleChoice group : playerMultiChoice.getSystems()) {
                        if (hitNumber >= group.getGroup()) {
                            double referenceOdd = lotterySystem.getGroupOdds(Math.min(group.getGroup(), playerMultiChoice.getMaxGroup()));
                            map.get(hitNumber).add(new WinningCombination(new Combination(group.getGroup(), hitNumber), referenceOdd));
                        }
                    }
                }

                return map;
            }

            public List<WinningCombination> getCombinationsForHit(int hit) {
                return this.winningCombinations.get(hit);
            }


            public double calculatePossibleWin(int hit, double stake) {
                List<WinningCombination> particularCombinations = this.winningCombinations.get(hit);
                double win = 0d;
                for (WinningCombination c : particularCombinations) {
                    win += stake * c.getOdds() * (double) c.getCombination().getCombi();
                }
                return win;
            }


            public List<Integer> getHits() {
                List<Integer> hits = new ArrayList<>(this.winningCombinations.keySet());
                Collections.sort(hits, Comparator.comparing(hit -> hit.intValue()));
                return hits;
            }


        }
    }


    /**
     * Lottery betslip leg
     */
    private static final class Hit {
        private final PlayerMultiChoice playerChoice;
        private final LotterySystem lotterySystem;
        private final int hit;
        private final List<WinningCombination> combinations;
        private final double stake;
        private final double win;

        public Hit(LotterySystem lotterySystem,
                   PlayerMultiChoice playerChoice,
                   int hit, Collection<WinningCombination> combinations, double stake, double win) {
            this.lotterySystem = lotterySystem;
            this.playerChoice = playerChoice;
            this.hit = hit;
            this.combinations = new ArrayList<>(combinations);
            this.stake = stake;
            this.win = win;
        }

        private double calculateProbability(int hit) {
            long x = (new Combination(hit, lotterySystem.getSelected())).getCombi();
            long y = (new Combination(lotterySystem.getSelected() - hit, lotterySystem.getTotal() - lotterySystem.getSelected())).getCombi();
            long z = lotterySystem.getCombination().getCombi();
            return (double) (x*y)/z;
        }

        private long getTotalCombinations() {
            return combinations.stream().mapToLong(item -> item.combination.getCombi()).sum();
        }


        @Override
        public String toString() {
            return "HIT=" + hit +
                    ", Stake=" + DF_8.format(stake) +
                    ", WIN=" + DF_2.format(win) +
                    ", Winning Comb. Total=" + getTotalCombinations() +
                    ", Winning Comb.=" + combinations +
                    ", probability=" + DF_8.format(calculateProbability(hit) * 100) + "% " +
                    ", offeredOdds=" + win/stake
                    //", fairOdds=" + calculateProbability(hit) / (1-calculateProbability(hit))
                    ;
        }
    }

    /**
     * Player selection
     */
    private static final class PlayerMultiChoice {
        private final Map<Integer, PlayerSingleChoice> systems;
        private final int[] numbers;
        private final double stake;

        public PlayerMultiChoice(Collection<PlayerSingleChoice> systems, int[] numbers, double stake) {
            this.systems = new LinkedHashMap<>();
            systems.forEach(item -> this.systems.put(item.getGroup(), item));
            this.numbers = numbers;
            this.stake = stake;
        }

        public double getStake() {
            return this.stake;
        }

        public List<PlayerSingleChoice> getSystems() {
            return new ArrayList<>(systems.values());
        }

        public int getBullets() {
            return numbers.length;
        }


        public int getMinGroup() {
            return systems.keySet().stream().min(Integer::compareTo).get();
        }

        public int getMaxGroup() {
            return systems.keySet().stream().max(Integer::compareTo).get();
        }

        public long getTotalCombinationsCount() {
            return systems.values().stream().mapToLong(item -> item.getCombination().getCombi()).sum();
        }

        @Override
        public String toString() {
            return "Player Selection: " +
                    System.lineSeparator() +
                    "-----------------------------------------------------" +
                    System.lineSeparator() +
                    "- selected numbers: " +  Arrays.toString(numbers) +
                    System.lineSeparator() +
                    "- selected systems: " + systems +
                    System.lineSeparator() +
                    "- stake: " + stake +
                    System.lineSeparator() +
                    "- virtual tickets: " + getTotalCombinationsCount();
        }
    }

    /**
     * Player single selection
     */
    private static class PlayerSingleChoice {
        private final Combination hitGroup;

        public PlayerSingleChoice(int group, int from) {
            this.hitGroup = new Combination(group, from);
        }

        public Combination getCombination() {
            return this.hitGroup;
        }

        public int getGroup() {
            return hitGroup.getK();
        }

        @Override
        public String toString() {
            return hitGroup.toString();
        }
    }

    /**
     * Winning combination decorated with assigned odds
     */
    private static class WinningCombination {
        private final Combination combination;
        private final double odds;

        public WinningCombination(Combination combination, double odds) {
            this.combination = combination;
            this.odds = odds;
        }

        public Combination getCombination() {
            return combination;
        }

        public double getOdds() {
            return odds;
        }

        @Override
        public String toString() {
            return "{" + combination.toString() + " odd: " + odds + "}";
        }
    }

    /**
     * Combination (k/n)
     */
    private static class Combination {
        private final int k;
        private final int n;
        private final long combi;

        public Combination(int k, int n) {
            this.k = k;
            this.n = n;
            this.combi = CombinatoricsUtils.binomialCoefficient(n, k);
        }

        public int getK() {
            return k;
        }

        public int getN() {
            return n;
        }

        public long getCombi() {
            return this.combi;
        }

        @Override
        public String toString() {
            return k + "/" + n + " (" + getCombi() + ")";
        }
    }


}
