package org.example;

import java.util.Random;
import java.util.random.RandomGenerator;

class Coin {
    String flip(String title) {
        Random r = Random.from(RandomGenerator.getDefault());
        if (r.nextBoolean()) {
            return title+" head";
        } else {
            return title+" tail";
        }
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Tossing coin");
        Thread.sleep(1000);
        var coin = new Coin();
        System.out.println(coin.flip("Result is"));
    }
}