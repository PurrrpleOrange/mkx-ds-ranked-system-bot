package com.mkx.ranked.config;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class DatabaseManager {

    private static SessionFactory sessionFactory;

    public static void init() {
        try {
            sessionFactory = new Configuration().configure("hibernate.cfg.xml").buildSessionFactory();
            System.out.println("✅ Подключение к БД Supabase успешно установлено!");
        } catch (Throwable ex) {
            System.err.println("❌ Ошибка инициализации SessionFactory:" + ex);
            throw new ExceptionInInitializerError(ex);
        }
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