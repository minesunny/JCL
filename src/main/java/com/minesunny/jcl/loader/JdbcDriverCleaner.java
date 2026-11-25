package com.minesunny.jcl.loader;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;

public class JdbcDriverCleaner implements Runnable {
    @Override
    public void run() {
        try {
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver d = drivers.nextElement();
                if (d.getClass().getClassLoader() == this.getClass().getClassLoader()) {
                    DriverManager.deregisterDriver(d);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
