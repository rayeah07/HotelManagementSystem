
package com.hotel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;
import java.io.Console;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class GrandStayRecovery {

    private static final int ITERATIONS = 120000;
    private static final int KEY_BITS = 256;
    private static final String USERNAME = "grandstay_admin";

    public static void main(String[] args) {

        Console console = System.console();

        if (console == null) {
            System.out.println(
                "Please run this program directly from Windows CMD."
            );
            return;
        }

        char[] password = console.readPassword(
            "Enter a new password for Grand Stay: "
        );

        char[] confirm = console.readPassword(
            "Confirm your new password: "
        );

        if (password == null || confirm == null ||
                password.length < 8 ||
                !Arrays.equals(password, confirm)) {

            System.out.println(
                "Passwords must match and contain at least 8 characters."
            );

            if (password != null) Arrays.fill(password, '\0');
            if (confirm != null) Arrays.fill(confirm, '\0');
            return;
        }

        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            String encodedSalt = Base64.getEncoder()
                    .encodeToString(salt);

            String encodedHash = hashPassword(password, salt);

            try (Connection con =
                    DatabaseConnection.getConnection()) {

                con.setAutoCommit(false);

                try {
                    String hotelName;

                    try (PreparedStatement check =
                            con.prepareStatement(
                                "SELECT hotel_name, username " +
                                "FROM hotels WHERE hotel_id=1 FOR UPDATE"
                            )) {

                        try (ResultSet rs = check.executeQuery()) {

                            if (!rs.next()) {
                                throw new SQLException(
                                    "Grand Stay account was not found."
                                );
                            }

                            hotelName = rs.getString("hotel_name");

                            String currentUsername =
                                rs.getString("username");

                            if (!"Grand Stay Hotel".equalsIgnoreCase(
                                    hotelName.trim())) {
                                throw new SQLException(
                                    "Hotel ID 1 is not Grand Stay Hotel."
                                );
                            }

                            if (currentUsername != null &&
                                    !currentUsername.isBlank()) {
                                throw new SQLException(
                                    "A username is already configured. " +
                                    "No changes were made."
                                );
                            }
                        }
                    }

                    try (PreparedStatement duplicate =
                            con.prepareStatement(
                                "SELECT COUNT(*) FROM hotels " +
                                "WHERE username=? AND hotel_id<>1"
                            )) {

                        duplicate.setString(1, USERNAME);

                        try (ResultSet rs = duplicate.executeQuery()) {
                            rs.next();

                            if (rs.getInt(1) > 0) {
                                throw new SQLException(
                                    "Username is already used by another hotel."
                                );
                            }
                        }
                    }

                    try (PreparedStatement update =
                            con.prepareStatement(
                                "UPDATE hotels SET username=?, " +
                                "password_hash=?, password_salt=? " +
                                "WHERE hotel_id=1 " +
                                "AND hotel_name='Grand Stay Hotel' " +
                                "AND (username IS NULL OR TRIM(username)='')"
                            )) {

                        update.setString(1, USERNAME);
                        update.setString(2, encodedHash);
                        update.setString(3, encodedSalt);

                        if (update.executeUpdate() != 1) {
                            throw new SQLException(
                                "Account was not updated. No changes made."
                            );
                        }
                    }

                    con.commit();

                    System.out.println();
                    System.out.println(
                        "GRAND STAY LOGIN RESTORED SUCCESSFULLY!"
                    );
                    System.out.println("--------------------------------");
                    System.out.println("Username: " + USERNAME);
                    System.out.println("Hotel ID: 1");
                    System.out.println(
                        "Your password was securely saved."
                    );
                    System.out.println("--------------------------------");

                } catch (Exception ex) {
                    con.rollback();
                    throw ex;
                }
            }

        } catch (Exception ex) {
            System.out.println(
                "RECOVERY FAILED: " + ex.getMessage()
            );
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private static String hashPassword(
            char[] password, byte[] salt) throws Exception {

        PBEKeySpec spec = new PBEKeySpec(
            password,
            salt,
            ITERATIONS,
            KEY_BITS
        );

        try {
            byte[] hash = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();

            return Base64.getEncoder()
                .encodeToString(hash);

        } finally {
            spec.clearPassword();
        }
    }
}
