package com.hotel;

import java.sql.Connection;

public class DatabaseTest {

    public static void main(String[] args) {

        try {

            Connection con =
                    DatabaseConnection.getConnection();

            System.out.println("--------------------------------");
            System.out.println("DATABASE CONNECTION SUCCESSFUL");
            System.out.println("--------------------------------");

            con.close();

        } catch (Exception e) {

            System.out.println("DATABASE CONNECTION FAILED");
            e.printStackTrace();
        }
    }
}