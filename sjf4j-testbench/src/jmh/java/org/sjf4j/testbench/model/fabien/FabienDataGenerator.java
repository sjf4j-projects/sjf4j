package org.sjf4j.testbench.model.fabien;

import org.apache.commons.lang3.RandomStringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

/**
 * Reproduces the workload shape of java-json-benchmark's UsersGenerator and
 * ClientsGenerator (default: one approximately 1 KB payload).
 *
 * <p>Keep a separate RNG per model: java-json-benchmark runs each data type
 * independently, with RandomUtils seeded from the SEED environment variable.
 * For comparable strings, this uses the same Commons Lang RandomStringUtils
 * overload as the upstream generator.
 *
 * <p>The upstream generators estimate JSON sizes while populating objects,
 * rather than guaranteeing exact byte lengths. We retain their estimates and
 * early-exit rules so array/list length distributions stay comparable.
 */
public final class FabienDataGenerator {

    private FabienDataGenerator() {
    }

    public static Users users(int targetBytes, long seed) {
        Random random = new Random(seed);
        Users root = new Users();
        root.setUsers(new ArrayList<>());
        int approxSize = 12; // {"users":[]}

        while (approxSize < targetBytes) {
            approxSize += appendUser(root, targetBytes - approxSize, random);
            approxSize++; // comma
        }
        return root;
    }

    private static int appendUser(Users root, int available, Random random) {
        int size = 2; // {}
        Users.User user = new Users.User();

        user.setId(numeric(random, 20));
        size += 9 + user.getId().length();
        user.setIndex(random.nextInt(Integer.MAX_VALUE));
        size += 11 + Integer.toString(user.getIndex()).length();
        user.setGuid(alphanumeric(random, 20));
        size += 10 + user.getGuid().length();
        user.setIsActive(random.nextInt(2) == 1);
        size += 17 + (user.getIsActive() ? 4 : 5);
        user.setBalance(alphanumeric(random, 20));
        size += 16 + user.getBalance().length();
        user.setPicture(alphanumeric(random, 100));
        size += 16 + user.getPicture().length();
        user.setAge(random.nextInt(100));
        size += 9 + Integer.toString(user.getAge()).length();
        user.setEyeColor(alphanumeric(random, 20));
        size += 17 + user.getEyeColor().length();
        user.setName(alphanumeric(random, 20));
        size += 10 + user.getName().length();
        user.setGender(alphanumeric(random, 20));
        size += 12 + user.getGender().length();
        user.setCompany(alphanumeric(random, 20));
        size += 13 + user.getCompany().length();
        user.setEmail(alphanumeric(random, 20));
        size += 11 + user.getEmail().length();
        user.setPhone(alphanumeric(random, 20));
        size += 11 + user.getPhone().length();
        user.setAddress(alphanumeric(random, 20));
        size += 13 + user.getAddress().length();
        user.setAbout(alphanumeric(random, 20));
        size += 11 + user.getAbout().length();
        user.setRegistered(alphanumeric(random, 20));
        size += 16 + user.getRegistered().length();
        user.setLatitude(random.nextDouble() * 90);
        size += 14 + Double.toString(user.getLatitude()).length();
        user.setLongitude(random.nextDouble() * 180);
        size += 15 + Double.toString(user.getLongitude()).length();

        user.setTags(new ArrayList<>());
        size += 10;
        int tagCount = random.nextInt(50);
        for (int i = 0; i < tagCount; i++) {
            if (size > available) break;
            String tag = alphanumeric(random, 10);
            user.getTags().add(tag);
            size += tag.length();
        }

        user.setFriends(new ArrayList<>());
        size += 13;
        int friendCount = random.nextInt(50);
        for (int i = 0; i < friendCount; i++) {
            if (size > available) break;
            String id = Integer.toString(random.nextInt(10000));
            String name = alphabetic(random, 30);
            user.getFriends().add(Users.Friend.create(id, name));
            size += id.length() + name.length() + 20;
        }

        user.setGreeting(alphanumeric(random, 20));
        size += 14 + user.getGreeting().length();
        user.setFavoriteFruit(alphanumeric(random, 20));
        size += 19 + user.getFavoriteFruit().length();

        root.getUsers().add(user);
        return size;
    }

    public static Clients clients(int targetBytes, long seed) {
        Random random = new Random(seed);
        Clients root = new Clients();
        root.setClients(new ArrayList<>());
        int approxSize = 14; // {"clients":[]}

        while (approxSize < targetBytes) {
            approxSize += appendClient(root, targetBytes - approxSize, random);
            approxSize++; // comma
        }
        return root;
    }

    private static int appendClient(Clients root, int available, Random random) {
        int size = 2; // {}
        Clients.Client client = new Clients.Client();

        client.setId(Math.abs(random.nextLong()));
        size += 9 + Long.toString(client.getId()).length();
        client.setIndex(random.nextInt(Integer.MAX_VALUE));
        size += 11 + Integer.toString(client.getIndex()).length();
        client.setGuid(new UUID(random.nextLong(), random.nextLong()));
        size += 10 + 36;
        client.setIsActive(random.nextInt(2) == 1);
        size += 17 + (client.getIsActive() ? 4 : 5);
        client.setBalance(BigDecimal.valueOf(random.nextDouble()));
        size += 16 + client.getBalance().toPlainString().length();
        client.setPicture(alphanumeric(random, 100));
        size += 16 + client.getPicture().length();
        client.setAge(random.nextInt(100));
        size += 9 + Integer.toString(client.getAge()).length();
        client.setEyeColor(Clients.EyeColor.fromNumber(random.nextInt(3)));
        size += 17 + client.getEyeColor().name().length();
        client.setName(alphanumeric(random, 20));
        size += 10 + client.getName().length();
        client.setGender(alphanumeric(random, 20));
        size += 12 + client.getGender().length();
        client.setCompany(alphanumeric(random, 20));
        size += 13 + client.getCompany().length();

        int emailCount = random.nextInt(10);
        String[] emails = new String[emailCount];
        int emailSize = 0;
        for (int i = 0; i < emailCount; i++) {
            emails[i] = alphabetic(random, 20);
            emailSize += 3 + emails[i].length();
        }
        client.setEmails(emails);
        size += 11 + emailSize;

        int phoneCount = random.nextInt(10);
        long[] phones = new long[phoneCount];
        int phoneSize = 0;
        for (int i = 0; i < phoneCount; i++) {
            phones[i] = Math.abs(random.nextInt());
            phoneSize += 1 + Long.toString(phones[i]).length();
        }
        client.setPhones(phones);
        size += 11 + phoneSize;

        client.setAddress(alphanumeric(random, 20));
        size += 13 + client.getAddress().length();
        client.setAbout(alphanumeric(random, 20));
        size += 11 + client.getAbout().length();
        client.setRegistered(LocalDate.of(1900 + random.nextInt(110),
                1 + random.nextInt(12), 1 + random.nextInt(28)));
        size += 16 + 10;
        client.setLatitude(random.nextDouble() * 90);
        size += 14 + Double.toString(client.getLatitude()).length();
        client.setLongitude(random.nextDouble() * 180);
        size += 15 + Double.toString(client.getLongitude()).length();

        client.setTags(new ArrayList<>());
        size += 10;
        int tagCount = random.nextInt(50);
        for (int i = 0; i < tagCount; i++) {
            if (size > available) break;
            String tag = alphanumeric(random, 10);
            client.getTags().add(tag);
            size += tag.length();
        }

        client.setPartners(new ArrayList<>());
        size += 13;
        int partnerCount = random.nextInt(30);
        for (int i = 0; i < partnerCount; i++) {
            if (size > available) break;
            long id = random.nextLong();
            String name = alphabetic(random, 30);
            OffsetDateTime since = OffsetDateTime.of(
                    1900 + random.nextInt(110),
                    1 + random.nextInt(12),
                    1 + random.nextInt(28),
                    random.nextInt(24),
                    random.nextInt(60),
                    random.nextInt(60),
                    random.nextInt(1_000_000_000),
                    ZoneOffset.UTC);
            client.getPartners().add(Clients.Partner.create(id, name, since));
            size += Long.toString(id).length() + name.length() + 50;
        }

        root.getClients().add(client);
        return size;
    }

    private static String numeric(Random random, int count) {
        return RandomStringUtils.random(count, 0, 0, false, true, null, random);
    }

    private static String alphabetic(Random random, int count) {
        return RandomStringUtils.random(count, 0, 0, true, false, null, random);
    }

    private static String alphanumeric(Random random, int count) {
        return RandomStringUtils.random(count, 0, 0, true, true, null, random);
    }
}
