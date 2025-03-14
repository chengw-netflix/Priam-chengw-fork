/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.priam.utils;

import static com.netflix.priam.utils.TokenManager.MAXIMUM_TOKEN_RANDOM;
import static com.netflix.priam.utils.TokenManager.MINIMUM_TOKEN_RANDOM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.common.collect.ImmutableList;
import com.netflix.priam.config.FakeConfiguration;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class RandomTokenManagerTest {
    private FakeConfiguration config;
    private TokenManager tokenManager;

    @Before
    public void setUp() {
        this.config = new FakeConfiguration();
        this.tokenManager = new TokenManager(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void initialToken_zeroSize() {
        tokenManager.initialToken(0, 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void initialToken_negativePosition() {
        tokenManager.initialToken(1, -1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void initialToken_negativeOffset() {
        tokenManager.initialToken(1, 0, -1);
    }

    @Test
    public void initialToken_positionZero() {
        assertEquals(MINIMUM_TOKEN_RANDOM, tokenManager.initialToken(1, 0, 0));
        assertEquals(MINIMUM_TOKEN_RANDOM, tokenManager.initialToken(10, 0, 0));
        assertEquals(MINIMUM_TOKEN_RANDOM, tokenManager.initialToken(133, 0, 0));
    }

    @Test
    public void initialToken_offsets_zeroPosition() {
        assertEquals(
                MINIMUM_TOKEN_RANDOM.add(BigInteger.valueOf(7)),
                tokenManager.initialToken(1, 0, 7));
        assertEquals(
                MINIMUM_TOKEN_RANDOM.add(BigInteger.valueOf(11)),
                tokenManager.initialToken(2, 0, 11));
        assertEquals(
                MINIMUM_TOKEN_RANDOM.add(BigInteger.valueOf(Integer.MAX_VALUE)),
                tokenManager.initialToken(256, 0, Integer.MAX_VALUE));
    }

    @Test
    public void initialToken_cannotExceedMaximumToken() {
        final int maxRingSize = Integer.MAX_VALUE;
        final int maxPosition = maxRingSize - 1;
        final int maxOffset = Integer.MAX_VALUE;
        assertEquals(
                1,
                MAXIMUM_TOKEN_RANDOM.compareTo(
                        tokenManager.initialToken(maxRingSize, maxPosition, maxOffset)));
    }

    @Test
    public void createToken() {
        assertEquals(
                MAXIMUM_TOKEN_RANDOM
                        .divide(BigInteger.valueOf(256))
                        .multiply(BigInteger.TEN)
                        .add(BigInteger.valueOf(tokenManager.regionOffset("region")))
                        .toString(),
                tokenManager.createToken(10, 256, "region"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void findClosestToken_emptyTokenList() {
        tokenManager.findClosestToken(BigInteger.ZERO, Collections.<BigInteger>emptyList());
    }

    @Test
    public void findClosestToken_singleTokenList() {
        final BigInteger onlyToken = BigInteger.valueOf(100);
        assertEquals(
                onlyToken,
                tokenManager.findClosestToken(BigInteger.TEN, ImmutableList.of(onlyToken)));
    }

    @Test
    public void findClosestToken_multipleTokenList() {
        List<BigInteger> tokenList =
                ImmutableList.of(BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(100));
        assertEquals(BigInteger.ONE, tokenManager.findClosestToken(BigInteger.ONE, tokenList));
        assertEquals(
                BigInteger.TEN, tokenManager.findClosestToken(BigInteger.valueOf(9), tokenList));
        assertEquals(BigInteger.TEN, tokenManager.findClosestToken(BigInteger.TEN, tokenList));
        assertEquals(
                BigInteger.TEN, tokenManager.findClosestToken(BigInteger.valueOf(12), tokenList));
        assertEquals(
                BigInteger.TEN, tokenManager.findClosestToken(BigInteger.valueOf(51), tokenList));
        assertEquals(
                BigInteger.valueOf(100),
                tokenManager.findClosestToken(BigInteger.valueOf(56), tokenList));
        assertEquals(
                BigInteger.valueOf(100),
                tokenManager.findClosestToken(BigInteger.valueOf(100), tokenList));
    }

    @Test
    public void findClosestToken_tieGoesToLargerToken() {
        assertEquals(
                BigInteger.TEN,
                tokenManager.findClosestToken(
                        BigInteger.valueOf(5), ImmutableList.of(BigInteger.ZERO, BigInteger.TEN)));
    }

    @Test
    public void test4Splits() {
        // example tokens from http://wiki.apache.org/cassandra/Operations

        final String expectedTokens =
                "0,42535295865117307932921825928971026432,"
                        + "85070591730234615865843651857942052864,127605887595351923798765477786913079296";
        String[] tokens = expectedTokens.split(",");
        int splits = tokens.length;
        for (int i = 0; i < splits; i++)
            assertEquals(new BigInteger(tokens[i]), tokenManager.initialToken(splits, i, 0));
    }

    @Test
    public void test16Splits() {
        final String expectedTokens =
                "0,10633823966279326983230456482242756608,"
                        + "21267647932558653966460912964485513216,31901471898837980949691369446728269824,"
                        + "42535295865117307932921825928971026432,53169119831396634916152282411213783040,"
                        + "63802943797675961899382738893456539648,74436767763955288882613195375699296256,"
                        + "85070591730234615865843651857942052864,95704415696513942849074108340184809472,"
                        + "106338239662793269832304564822427566080,116972063629072596815535021304670322688,"
                        + "127605887595351923798765477786913079296,138239711561631250781995934269155835904,"
                        + "148873535527910577765226390751398592512,159507359494189904748456847233641349120";
        String[] tokens = expectedTokens.split(",");
        int splits = tokens.length;
        for (int i = 0; i < splits; i++)
            assertEquals(new BigInteger(tokens[i]), tokenManager.initialToken(splits, i, 0));
    }

    @Test
    public void regionOffset() {
        String allRegions = "us-west-2,us-east,us-west,eu-east,eu-west,ap-northeast,ap-southeast";

        for (String region1 : allRegions.split(","))
            for (String region2 : allRegions.split(",")) {
                if (region1.equals(region2)) continue;
                assertFalse(
                        "Diffrence seems to be low",
                        Math.abs(
                                        tokenManager.regionOffset(region1)
                                                - tokenManager.regionOffset(region2))
                                < 100);
            }
    }

    @Test
    public void testMultiToken() {
        int h1 = tokenManager.regionOffset("vijay");
        int h2 = tokenManager.regionOffset("vijay2");
        BigInteger t1 = tokenManager.initialToken(100, 10, h1);
        BigInteger t2 = tokenManager.initialToken(100, 10, h2);

        BigInteger tokendistance = t1.subtract(t2).abs();
        int hashDiffrence = h1 - h2;

        assertEquals(new BigInteger("" + hashDiffrence).abs(), tokendistance);

        BigInteger t3 = tokenManager.initialToken(100, 99, h1);
        BigInteger t4 = tokenManager.initialToken(100, 99, h2);
        tokendistance = t3.subtract(t4).abs();

        assertEquals(new BigInteger("" + hashDiffrence).abs(), tokendistance);
    }
}
