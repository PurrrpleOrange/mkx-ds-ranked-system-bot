package com.mkx.ranked.config;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class DatabaseManager {

    private static SessionFactory sessionFactory;

    public static void init() {
        try {
            String dbUrl = getRequiredEnv("DB_URL");
            String dbUsername = getRequiredEnv("DB_USERNAME");
            String dbPassword = getRequiredEnv("DB_PASSWORD");

            Configuration configuration = new Configuration()
                    .configure("hibernate.cfg.xml")
                    .setProperty("hibernate.connection.url", dbUrl)
                    .setProperty("hibernate.connection.username", dbUsername)
                    .setProperty("hibernate.connection.password", dbPassword);

            sessionFactory = configuration.buildSessionFactory();

            System.out.println("✅ Подключение к БД Supabase успешно установлено!");
        } catch (Throwable ex) {
            System.err.println("❌ Ошибка инициализации SessionFactory: " + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Переменная окружения " + name + " не установлена"
            );
        }

        return value;
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}