package com.hotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HotelServer {

    private static final int PORT = 8080;
    private static final String COOKIE_NAME = "HMS_SESSION";

    private static final Map<String, Integer> SESSIONS =
            new ConcurrentHashMap<>();

    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws Exception {
        initializeGrandStayAccount();

        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT), 0);

        server.createContext("/", HotelServer::home);
        server.createContext("/login", HotelServer::login);
        server.createContext("/register", HotelServer::register);
        server.createContext("/logout", HotelServer::logout);
        server.createContext("/change-password", HotelServer::changePassword);

        server.createContext("/dashboard.html",
                e -> protectedPage(e, "dashboard.html"));
        server.createContext("/rooms.html",
                e -> protectedPage(e, "rooms.html"));
        server.createContext("/booking.html",
                e -> protectedPage(e, "booking.html"));
        server.createContext("/bookings.html",
                e -> protectedPage(e, "bookings.html"));
        server.createContext("/settings.html",
                e -> protectedPage(e, "settings.html"));
        server.createContext("/css/style.css",
                e -> serveFile(e, "css/style.css"));

        server.createContext("/dashboard-data", HotelServer::dashboardData);
        server.createContext("/all-rooms", HotelServer::allRooms);
        server.createContext("/available-rooms", HotelServer::availableRooms);
        server.createContext("/booking-history", HotelServer::bookingHistory);
        server.createContext("/book", HotelServer::book);
        server.createContext("/cancel-booking", HotelServer::cancelBooking);
        server.createContext("/checkout", HotelServer::checkout);
        server.createContext("/room-service", HotelServer::roomService);
        server.createContext("/service-list", HotelServer::serviceList);
        server.createContext("/invoice", HotelServer::invoice);
        server.createContext("/invoice-pdf", HotelServer::invoicePdf);

        server.createContext("/pricing-data", HotelServer::pricingData);
        server.createContext("/update-room-prices",
                HotelServer::updateRoomPrices);
        server.createContext("/update-service-price",
                HotelServer::updateServicePrice);
        server.createContext("/add-floor", HotelServer::addFloor);
        server.createContext("/delete-floor", HotelServer::deleteFloor);

        server.start();

        System.out.println("======================================");
        System.out.println("       HOTEL MANAGEMENT SYSTEM");
        System.out.println("======================================");
        System.out.println("Server running at http://localhost:" + PORT);
        System.out.println("Press Ctrl+C to stop the server.");
    }

    // INITIAL GRAND STAY ACCOUNT

    private static void initializeGrandStayAccount()
            throws SQLException {

        String username = "grandstay_admin";
        String password = "GS!Stay#8F2qL9vR";

        try (Connection con = DatabaseConnection.getConnection()) {

            String sql = "SELECT username FROM hotels WHERE hotel_id=1";

            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    throw new SQLException(
                            "Grand Stay hotel record was not found.");
                }

                if (rs.getString("username") != null) {
                    System.out.println(
                            "Grand Stay login already exists.");
                    return;
                }
            }

            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);

            String update =
                    "UPDATE hotels SET username=?,password_hash=?, " +
                    "password_salt=? WHERE hotel_id=1 AND username IS NULL";

            try (PreparedStatement ps = con.prepareStatement(update)) {

                ps.setString(1, username);
                ps.setString(2, hashPassword(password, salt));
                ps.setString(3,
                        Base64.getEncoder().encodeToString(salt));

                if (ps.executeUpdate() == 1) {
                    System.out.println(
                            "Initial Grand Stay login was created.");
                    System.out.println("Username: " + username);
                    System.out.println(
                            "Change the temporary password after logging in.");
                }
            }
        }
    }

    // PAGE ROUTES

    private static void home(HttpExchange e) throws IOException {
        if (getHotelId(e) != -1) {
            redirect(e, "/dashboard.html");
        } else {
            serveFile(e, "index.html");
        }
    }

    private static void protectedPage(
            HttpExchange e, String file) throws IOException {

        if (getHotelId(e) == -1) {
            redirect(e, "/");
            return;
        }

        serveFile(e, file);
    }

    // REGISTRATION

    private static void register(HttpExchange e) throws IOException {

        if (!isMethod(e, "POST")) {
            sendError(e, "Invalid request method.");
            return;
        }

        Map<String, String> d = parseForm(readBody(e));

        String hotelName = value(d, "hotelName");
        String username = value(d, "username");
        String email = value(d, "email");
        String password = d.getOrDefault("password", "");
        String confirm = d.getOrDefault("confirmPassword", "");

        if (hotelName.isBlank() || username.isBlank()
                || email.isBlank() || password.isBlank()) {
            sendError(e, "Please complete all registration fields.");
            return;
        }

        if (username.length() < 4 || password.length() < 8) {
            sendError(e,
                    "Username must have at least 4 characters and password 8 characters.");
            return;
        }

        if (!password.equals(confirm)) {
            sendError(e, "Passwords do not match.");
            return;
        }

        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                String sql =
                        "INSERT INTO hotels " +
                        "(hotel_name,username,email,password_hash,password_salt) " +
                        "VALUES (?,?,?,?,?)";

                int hotelId;

                try (PreparedStatement ps = con.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, hotelName);
                    ps.setString(2, username);
                    ps.setString(3, email);
                    ps.setString(4, hashPassword(password, salt));
                    ps.setString(5,
                            Base64.getEncoder().encodeToString(salt));

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Could not create hotel account.");
                        }
                        hotelId = keys.getInt(1);
                    }
                }

                createDefaultHotelSetup(con, hotelId);
                con.commit();

                messagePage(e,
                        "Registration successful",
                        "Your hotel account has been created. You can now log in.");

            } catch (SQLException ex) {
                con.rollback();

                if (ex.getMessage() != null
                        && ex.getMessage().toLowerCase().contains("duplicate")) {
                    sendError(e,
                            "That username or email is already registered.");
                } else {
                    throw ex;
                }

            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
            sendError(e, "Registration could not be completed.");
        }
    }

    private static void createDefaultHotelSetup(
            Connection con, int hotelId) throws SQLException {

        String[] types = {"Single Bed", "Double Bed", "Deluxe Bed"};
        double[] prices = {1000, 2500, 4000};

        String[] services = {
                "Breakfast", "Dinner", "Extra Bed", "Laundry",
                "Lunch", "Room Cleaning", "Tea / Coffee"
        };

        double[] servicePrices = {300, 600, 800, 250, 500, 200, 150};

        try (PreparedStatement floor = con.prepareStatement(
                "INSERT INTO hotel_floors " +
                "(hotel_id,floor_number,floor_name) VALUES (?,?,?)")) {

            floor.setInt(1, hotelId);
            floor.setInt(2, 1);
            floor.setString(3, "Floor 1");
            floor.executeUpdate();
        }

        try (PreparedStatement type = con.prepareStatement(
                "INSERT INTO room_types (hotel_id,room_type,price) VALUES (?,?,?)")) {

            for (int i = 0; i < types.length; i++) {
                type.setInt(1, hotelId);
                type.setString(2, types[i]);
                type.setDouble(3, prices[i]);
                type.addBatch();
            }

            type.executeBatch();
        }

        try (PreparedStatement room = con.prepareStatement(
                "INSERT INTO rooms " +
                "(room_number,floor_number,room_type,price,available,hotel_id) " +
                "VALUES (?,?,?,?,1,?)")) {

            int roomNumber = 101;

            for (int i = 0; i < types.length; i++) {
                room.setInt(1, roomNumber++);
                room.setInt(2, 1);
                room.setString(3, types[i]);
                room.setDouble(4, prices[i]);
                room.setInt(5, hotelId);
                room.addBatch();
            }

            room.executeBatch();
        }

        try (PreparedStatement service = con.prepareStatement(
                "INSERT INTO service_prices " +
                "(service_name,price,hotel_id) VALUES (?,?,?)")) {

            for (int i = 0; i < services.length; i++) {
                service.setString(1, services[i]);
                service.setDouble(2, servicePrices[i]);
                service.setInt(3, hotelId);
                service.addBatch();
            }

            service.executeBatch();
        }
    }

    // LOGIN

    private static void login(HttpExchange e) throws IOException {

        if (!isMethod(e, "POST")) {
            sendError(e, "Invalid request.");
            return;
        }

        Map<String, String> d = parseForm(readBody(e));

        String username = value(d, "username");
        String password = d.getOrDefault("password", "");

        String sql =
                "SELECT hotel_id,password_hash,password_salt " +
                "FROM hotels WHERE username=?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    sendError(e, "Invalid username or password.");
                    return;
                }

                int hotelId = rs.getInt("hotel_id");
                String savedHash = rs.getString("password_hash");
                String savedSalt = rs.getString("password_salt");

                if (savedHash == null || savedSalt == null) {
                    sendError(e,
                            "This account does not have a password set.");
                    return;
                }

                byte[] salt = Base64.getDecoder().decode(savedSalt);
                String enteredHash = hashPassword(password, salt);

                if (!MessageDigest.isEqual(
                        savedHash.getBytes(StandardCharsets.UTF_8),
                        enteredHash.getBytes(StandardCharsets.UTF_8))) {
                    sendError(e, "Invalid username or password.");
                    return;
                }

                String token = UUID.randomUUID().toString();
                SESSIONS.put(token, hotelId);

                e.getResponseHeaders().add(
                        "Set-Cookie",
                        COOKIE_NAME + "=" + token +
                        "; Path=/; HttpOnly; SameSite=Lax");

                redirect(e, "/dashboard.html");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(e, "Login could not be completed.");
        }
    }

    // LOGOUT

    private static void logout(HttpExchange e) throws IOException {

        String token = getSessionToken(e);

        if (token != null) {
            SESSIONS.remove(token);
        }

        e.getResponseHeaders().add(
                "Set-Cookie",
                COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");

        redirect(e, "/");
    }

    // CHANGE PASSWORD

    private static void changePassword(HttpExchange e)
            throws IOException {

        int hotelId = requireHotel(e);
        if (hotelId == -1) return;

        if (!isMethod(e, "POST")) {
            sendError(e, "Invalid request.");
            return;
        }

        Map<String, String> d = parseForm(readBody(e));

        String oldPassword = d.getOrDefault("oldPassword", "");
        String newPassword = d.getOrDefault("newPassword", "");
        String confirm = d.getOrDefault("confirmPassword", "");

        if (newPassword.length() < 8) {
            sendError(e, "New password must contain at least 8 characters.");
            return;
        }

        if (!newPassword.equals(confirm)) {
            sendError(e, "New passwords do not match.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            String sql =
                    "SELECT password_hash,password_salt " +
                    "FROM hotels WHERE hotel_id=?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setInt(1, hotelId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        sendError(e, "Hotel account not found.");
                        return;
                    }

                    String hash = rs.getString("password_hash");
                    byte[] salt = Base64.getDecoder().decode(
                            rs.getString("password_salt"));

                    if (!hashPassword(oldPassword, salt).equals(hash)) {
                        sendError(e, "Current password is incorrect.");
                        return;
                    }
                }
            }

            byte[] newSalt = new byte[16];
            RANDOM.nextBytes(newSalt);

            String update =
                    "UPDATE hotels SET password_hash=?,password_salt=? " +
                    "WHERE hotel_id=?";

            try (PreparedStatement ps = con.prepareStatement(update)) {

                ps.setString(1, hashPassword(newPassword, newSalt));
                ps.setString(2,
                        Base64.getEncoder().encodeToString(newSalt));
                ps.setInt(3, hotelId);
                ps.executeUpdate();
            }

            sendJson(e,
                    "{\"success\":true,\"message\":\"Password changed successfully.\"}");

        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(e, "Could not change password.");
        }
    }

    // SESSION HELPERS

    private static String getSessionToken(HttpExchange e) {

        List<String> cookies = e.getRequestHeaders().get("Cookie");

        if (cookies == null) return null;

        for (String cookieHeader : cookies) {
            for (String part : cookieHeader.split(";")) {

                String[] pair = part.trim().split("=", 2);

                if (pair.length == 2
                        && pair[0].equals(COOKIE_NAME)) {
                    return pair[1];
                }
            }
        }

        return null;
    }

    private static Integer getHotelId(HttpExchange e) {

        String token = getSessionToken(e);

        return token == null ? null : SESSIONS.get(token);
    }

    private static Integer requireHotel(HttpExchange e)
            throws IOException {

        Integer id = getHotelId(e);

        if (id == null) {
            sendJson(e,
                    "{\"success\":false,\"message\":\"Please log in.\"}");
        }

        return id;
    }

    private static String hashPassword(
            String password, byte[] salt) {

        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, 120000, 256);

            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] hash = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();

            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Password hashing failed.", ex);
        }
    }
        // =========================================================
    // DASHBOARD
    // =========================================================

    private static void dashboardData(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        String sql = """
            SELECT
                COUNT(*) AS total_rooms,
                SUM(CASE WHEN available = 1 THEN 1 ELSE 0 END) AS available_rooms,
                SUM(CASE WHEN available = 0 THEN 1 ELSE 0 END) AS occupied_rooms
            FROM rooms
            WHERE hotel_id = ?
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, hotelId);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();

                int totalRooms = rs.getInt("total_rooms");
                int availableRooms = rs.getInt("available_rooms");
                int occupiedRooms = rs.getInt("occupied_rooms");

                int activeBookings = scalarInt(
                    con,
                    "SELECT COUNT(*) FROM bookings WHERE hotel_id=? AND status='ACTIVE'",
                    hotelId
                );

                double revenue = scalarDouble(
                    con,
                    "SELECT COALESCE(SUM(total),0) FROM bookings WHERE hotel_id=? AND status='CHECKED_OUT'",
                    hotelId
                );

                String json = "{"
                    + "\"totalRooms\":" + totalRooms + ","
                    + "\"availableRooms\":" + availableRooms + ","
                    + "\"occupiedRooms\":" + occupiedRooms + ","
                    + "\"activeBookings\":" + activeBookings + ","
                    + "\"revenue\":" + revenue
                    + "}";

                sendJson(exchange, json);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(exchange, "Unable to load dashboard data.");
        }
    }


    // =========================================================
    // ROOMS
    // =========================================================

    private static void allRooms(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        String sql = """
            SELECT room_number, floor_number, room_type, price, available
            FROM rooms
            WHERE hotel_id = ?
            ORDER BY floor_number, room_number
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, hotelId);

            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    first = false;

                    json.append("{")
                        .append("\"roomNumber\":").append(rs.getInt("room_number")).append(",")
                        .append("\"floorNumber\":").append(rs.getInt("floor_number")).append(",")
                        .append("\"roomType\":\"").append(jsonEscape(rs.getString("room_type"))).append("\",")
                        .append("\"price\":").append(rs.getDouble("price")).append(",")
                        .append("\"available\":").append(rs.getBoolean("available"))
                        .append("}");
                }

                json.append("]");
                sendJson(exchange, json.toString());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(exchange, "Unable to load rooms.");
        }
    }


    private static void availableRooms(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        String sql = """
            SELECT room_number, floor_number, room_type, price
            FROM rooms
            WHERE hotel_id = ? AND available = 1
            ORDER BY floor_number, room_number
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, hotelId);

            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    first = false;

                    json.append("{")
                        .append("\"roomNumber\":").append(rs.getInt("room_number")).append(",")
                        .append("\"floorNumber\":").append(rs.getInt("floor_number")).append(",")
                        .append("\"roomType\":\"").append(jsonEscape(rs.getString("room_type"))).append("\",")
                        .append("\"price\":").append(rs.getDouble("price"))
                        .append("}");
                }

                json.append("]");
                sendJson(exchange, json.toString());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(exchange, "Unable to load available rooms.");
        }
    }


    // =========================================================
    // BOOKING
    // =========================================================

    private static void book(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> form = readForm(exchange);

        String name = form.getOrDefault("name", "").trim();
        String phone = form.getOrDefault("phone", "").trim();

        int roomNumber;
        int days;

        try {
            roomNumber = Integer.parseInt(form.getOrDefault("roomNumber", "0"));
            days = Integer.parseInt(form.getOrDefault("days", "0"));
        } catch (NumberFormatException e) {
            sendError(exchange, "Please enter valid room and day values.");
            return;
        }

        if (name.isBlank() || phone.isBlank() || days <= 0) {
            sendError(exchange, "Please fill in all booking details correctly.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                double roomPrice;

                String roomSql = """
                    SELECT price, available
                    FROM rooms
                    WHERE hotel_id=? AND room_number=?
                    FOR UPDATE
                    """;

                try (PreparedStatement ps = con.prepareStatement(roomSql)) {
                    ps.setInt(1, hotelId);
                    ps.setInt(2, roomNumber);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            sendError(exchange, "Room not found.");
                            return;
                        }

                        if (!rs.getBoolean("available")) {
                            con.rollback();
                            sendError(exchange, "This room is already booked.");
                            return;
                        }

                        roomPrice = rs.getDouble("price");
                    }
                }

                int customerId;

                String customerSql = """
                    INSERT INTO customers(name, phone, hotel_id)
                    VALUES (?, ?, ?)
                    """;

                try (PreparedStatement ps = con.prepareStatement(
                        customerSql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, name);
                    ps.setString(2, phone);
                    ps.setInt(3, hotelId);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Customer ID was not generated.");
                        }

                        customerId = keys.getInt(1);
                    }
                }

                double total = roomPrice * days;

                String bookingSql = """
                    INSERT INTO bookings
                    (customer_id, room_number, days, total, booking_date, status, hotel_id)
                    VALUES (?, ?, ?, ?, NOW(), 'ACTIVE', ?)
                    """;

                int bookingId;

                try (PreparedStatement ps = con.prepareStatement(
                        bookingSql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setInt(1, customerId);
                    ps.setInt(2, roomNumber);
                    ps.setInt(3, days);
                    ps.setDouble(4, total);
                    ps.setInt(5, hotelId);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Booking ID was not generated.");
                        }

                        bookingId = keys.getInt(1);
                    }
                }

                String updateRoom = """
                    UPDATE rooms
                    SET available=0
                    WHERE hotel_id=? AND room_number=?
                    """;

                try (PreparedStatement ps = con.prepareStatement(updateRoom)) {
                    ps.setInt(1, hotelId);
                    ps.setInt(2, roomNumber);
                    ps.executeUpdate();
                }

                con.commit();

                sendJson(exchange,
                    "{\"success\":true,\"message\":\"Booking successful.\",\"bookingId\":" + bookingId + "}");

            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to complete booking.");
        }
    }


    // =========================================================
    // BOOKING HISTORY
    // =========================================================

    private static void bookingHistory(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        String sql = """
            SELECT
                b.booking_id,
                b.customer_id,
                c.name,
                c.phone,
                b.room_number,
                r.floor_number,
                r.room_type,
                b.days,
                b.total,
                b.booking_date,
                b.status,
                b.checkout_date,
                COALESCE(
                    (SELECT SUM(rs.total)
                     FROM room_services rs
                     WHERE rs.hotel_id=b.hotel_id
                       AND rs.booking_id=b.booking_id), 0
                ) AS service_total
            FROM bookings b
            JOIN customers c
              ON c.hotel_id=b.hotel_id
             AND c.customer_id=b.customer_id
            JOIN rooms r
              ON r.hotel_id=b.hotel_id
             AND r.room_number=b.room_number
            WHERE b.hotel_id=?
            ORDER BY b.booking_id DESC
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, hotelId);

            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    first = false;

                    json.append("{")
                        .append("\"bookingId\":").append(rs.getInt("booking_id")).append(",")
                        .append("\"customerId\":").append(rs.getInt("customer_id")).append(",")
                        .append("\"customerName\":\"").append(jsonEscape(rs.getString("name"))).append("\",")
                        .append("\"phone\":\"").append(jsonEscape(rs.getString("phone"))).append("\",")
                        .append("\"roomNumber\":").append(rs.getInt("room_number")).append(",")
                        .append("\"floorNumber\":").append(rs.getInt("floor_number")).append(",")
                        .append("\"roomType\":\"").append(jsonEscape(rs.getString("room_type"))).append("\",")
                        .append("\"days\":").append(rs.getInt("days")).append(",")
                        .append("\"total\":").append(rs.getDouble("total")).append(",")
                        .append("\"serviceTotal\":").append(rs.getDouble("service_total")).append(",")
                        .append("\"bookingDate\":\"").append(jsonEscape(String.valueOf(rs.getTimestamp("booking_date")))).append("\",")
                        .append("\"status\":\"").append(jsonEscape(rs.getString("status"))).append("\",")
                        .append("\"checkoutDate\":\"")
                        .append(jsonEscape(rs.getTimestamp("checkout_date") == null
                            ? "" : String.valueOf(rs.getTimestamp("checkout_date"))))
                        .append("\"}");
                }

                json.append("]");
                sendJson(exchange, json.toString());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(exchange, "Unable to load booking history.");
        }
    }


    // =========================================================
    // CANCEL BOOKING
    // =========================================================

    private static void cancelBooking(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> form = readForm(exchange);

        int bookingId;

        try {
            bookingId = Integer.parseInt(form.getOrDefault("bookingId", "0"));
        } catch (NumberFormatException e) {
            sendError(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                int roomNumber;

                String selectSql = """
                    SELECT room_number, status
                    FROM bookings
                    WHERE hotel_id=? AND booking_id=?
                    FOR UPDATE
                    """;

                try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                    ps.setInt(1, hotelId);
                    ps.setInt(2, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            sendError(exchange, "Booking not found.");
                            return;
                        }

                        if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                            con.rollback();
                            sendError(exchange, "Only active bookings can be cancelled.");
                            return;
                        }

                        roomNumber = rs.getInt("room_number");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bookings SET status='CANCELLED' WHERE hotel_id=? AND booking_id=?")) {
                    ps.setInt(1, hotelId);
                    ps.setInt(2, bookingId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE rooms SET available=1 WHERE hotel_id=? AND room_number=?")) {
                    ps.setInt(1, hotelId);
                    ps.setInt(2, roomNumber);
                    ps.executeUpdate();
                }

                con.commit();
                sendJson(exchange, "{\"success\":true,\"message\":\"Booking cancelled successfully.\"}");

            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to cancel booking.");
        }
    }


    // =========================================================
    // CHECKOUT
    // =========================================================

    private static void checkout(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> form = readForm(exchange);

        int bookingId;

        try {
            bookingId = Integer.parseInt(form.getOrDefault("bookingId", "0"));
        } catch (NumberFormatException e) {
            sendError(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                int roomNumber;

                String selectSql = """
                    SELECT room_number, status
                    FROM bookings
                    WHERE hotel_id=? AND booking_id=?
                    FOR UPDATE
                    """;

                try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                    ps.setInt(1, hotelId);
                    ps.setInt(2, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            sendError(exchange, "Booking not found.");
                            return;
                        }

                        if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                            con.rollback();
                            sendError(exchange, "This booking is not active.");
                            return;
                        }

                        roomNumber = rs.getInt("room_number");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE bookings
                        SET status='CHECKED_OUT', checkout_date=NOW()
                        WHERE hotel_id=? AND booking_id=?
                        """)) {

                    ps.setInt(1, hotelId);
                    ps.setInt(2, bookingId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE rooms
                        SET available=1
                        WHERE hotel_id=? AND room_number=?
                        """)) {

                    ps.setInt(1, hotelId);
                    ps.setInt(2, roomNumber);
                    ps.executeUpdate();
                }

                con.commit();

                sendJson(exchange,
                    "{\"success\":true,\"message\":\"Checkout completed successfully.\",\"invoiceUrl\":\"/invoice?bookingId="
                    + bookingId + "\"}");

            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to complete checkout.");
        }
    }


    // =========================================================
    // ROOM SERVICES
    // =========================================================

    private static void serviceList(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        String sql = """
            SELECT service_name, price
            FROM service_prices
            WHERE hotel_id=?
            ORDER BY service_name
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, hotelId);

            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    first = false;

                    json.append("{")
                        .append("\"serviceName\":\"").append(jsonEscape(rs.getString("service_name"))).append("\",")
                        .append("\"price\":").append(rs.getDouble("price"))
                        .append("}");
                }

                json.append("]");
                sendJson(exchange, json.toString());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(exchange, "Unable to load services.");
        }
    }


    private static void roomService(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> form = readForm(exchange);

        int bookingId;
        int quantity;

        String serviceName = form.getOrDefault("serviceName", "").trim();

        try {
            bookingId = Integer.parseInt(form.getOrDefault("bookingId", "0"));
            quantity = Integer.parseInt(form.getOrDefault("quantity", "0"));
        } catch (NumberFormatException e) {
            sendError(exchange, "Invalid booking ID or quantity.");
            return;
        }

        if (serviceName.isBlank() || quantity <= 0) {
            sendError(exchange, "Please select a service and enter a valid quantity.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                String statusSql = """
                    SELECT status
                    FROM bookings
                    WHERE hotel_id=? AND booking_id=?
                    FOR UPDATE
                    """;

                try (PreparedStatement ps = con.prepareStatement(statusSql)) {
                    ps.setInt(1, hotelId);
                    ps.setInt(2, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            sendError(exchange, "Booking not found.");
                            return;
                        }

                        if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                            con.rollback();
                            sendError(exchange, "Services can only be added to active bookings.");
                            return;
                        }
                    }
                }

                double price;

                try (PreparedStatement ps = con.prepareStatement("""
                        SELECT price
                        FROM service_prices
                        WHERE hotel_id=? AND service_name=?
                        """)) {

                    ps.setInt(1, hotelId);
                    ps.setString(2, serviceName);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            sendError(exchange, "Service not found.");
                            return;
                        }

                        price = rs.getDouble("price");
                    }
                }

                double total = price * quantity;

                String insertSql = """
                    INSERT INTO room_services
                    (booking_id, service_name, quantity, price, total, service_date, hotel_id)
                    VALUES (?, ?, ?, ?, ?, NOW(), ?)
                    """;

                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    ps.setInt(1, bookingId);
                    ps.setString(2, serviceName);
                    ps.setInt(3, quantity);
                    ps.setDouble(4, price);
                    ps.setDouble(5, total);
                    ps.setInt(6, hotelId);
                    ps.executeUpdate();
                }

                con.commit();

                sendJson(exchange,
                    "{\"success\":true,\"message\":\"Service added successfully.\",\"total\":" + total + "}");

            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to add room service.");
        }
    }


    // =========================================================
    // INVOICE
    // =========================================================

    private static void invoice(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> query = queryParameters(exchange);

        int bookingId;

        try {
            bookingId = Integer.parseInt(query.getOrDefault("bookingId", "0"));
        } catch (NumberFormatException e) {
            sendError(exchange, "Invalid booking ID.");
            return;
        }

        String bookingSql = """
            SELECT
                b.booking_id,
                b.customer_id,
                c.name,
                c.phone,
                b.room_number,
                r.floor_number,
                r.room_type,
                r.price,
                b.days,
                b.total,
                b.booking_date,
                b.checkout_date,
                b.status
            FROM bookings b
            JOIN customers c
              ON c.hotel_id=b.hotel_id
             AND c.customer_id=b.customer_id
            JOIN rooms r
              ON r.hotel_id=b.hotel_id
             AND r.room_number=b.room_number
            WHERE b.hotel_id=? AND b.booking_id=?
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(bookingSql)) {

            ps.setInt(1, hotelId);
            ps.setInt(2, bookingId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendError(exchange, "Invoice not found.");
                    return;
                }

                String customerName = rs.getString("name");
                String phone = rs.getString("phone");
                int roomNumber = rs.getInt("room_number");
                int floorNumber = rs.getInt("floor_number");
                String roomType = rs.getString("room_type");
                double roomPrice = rs.getDouble("price");
                int days = rs.getInt("days");
                double roomTotal = rs.getDouble("total");
                String bookingDate = String.valueOf(rs.getTimestamp("booking_date"));
                Timestamp checkoutTimestamp = rs.getTimestamp("checkout_date");
                String status = rs.getString("status");

                StringBuilder serviceRows = new StringBuilder();
                double serviceTotal = 0;

                String serviceSql = """
                    SELECT service_name, quantity, price, total, service_date
                    FROM room_services
                    WHERE hotel_id=? AND booking_id=?
                    ORDER BY service_id
                    """;

                try (PreparedStatement servicePs = con.prepareStatement(serviceSql)) {
                    servicePs.setInt(1, hotelId);
                    servicePs.setInt(2, bookingId);

                    try (ResultSet serviceRs = servicePs.executeQuery()) {
                        while (serviceRs.next()) {
                            double lineTotal = serviceRs.getDouble("total");
                            serviceTotal += lineTotal;

                            serviceRows.append("<tr>")
                                .append("<td>").append(htmlEscape(serviceRs.getString("service_name"))).append("</td>")
                                .append("<td>").append(serviceRs.getInt("quantity")).append("</td>")
                                .append("<td>₹").append(money(serviceRs.getDouble("price"))).append("</td>")
                                .append("<td>₹").append(money(lineTotal)).append("</td>")
                                .append("</tr>");
                        }
                    }
                }

                double grandTotal = roomTotal + serviceTotal;

                if (serviceRows.length() == 0) {
                    serviceRows.append("<tr><td colspan='4'>No services added</td></tr>");
                }

                String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta charset="UTF-8">
                    <title>Hotel Invoice</title>
                    <style>
                    body{font-family:Arial,sans-serif;background:#f4f5f7;padding:30px;color:#222}
                    .invoice{max-width:800px;margin:auto;background:white;padding:35px;border-radius:12px}
                    h1{margin-bottom:5px;color:#263b53}
                    .muted{color:#666}
                    table{width:100%;border-collapse:collapse;margin-top:20px}
                    th,td{padding:12px;border-bottom:1px solid #ddd;text-align:left}
                    th{background:#f1f3f5}
                    .total{text-align:right;font-size:20px;font-weight:bold;margin-top:25px}
                    .actions{display:flex;gap:12px;margin-top:25px}
                    button{padding:11px 18px;border:0;border-radius:6px;cursor:pointer}
                    .print{background:#263b53;color:white}
                    .pdf{background:#167c59;color:white}
                    </style>
                    </head>
                    <body>
                    <div class="invoice">
                    <h1>Grand Stay</h1>
                    <p class="muted">Hotel Invoice</p>
                    <hr>
                    <h3>Booking Details</h3>
                    <p><b>Invoice / Booking ID:</b> %s</p>
                    <p><b>Customer:</b> %s</p>
                    <p><b>Phone:</b> %s</p>
                    <p><b>Room:</b> %s</p>
                    <p><b>Floor:</b> %s</p>
                    <p><b>Room Type:</b> %s</p>
                    <p><b>Price per Day:</b> ₹%s</p>
                    <p><b>Days:</b> %s</p>
                    <p><b>Booking Date:</b> %s</p>
                    <p><b>Checkout Date:</b> %s</p>
                    <p><b>Status:</b> %s</p>
                    <h3>Room Charges</h3>
                    <table>
                    <tr><th>Description</th><th>Amount</th></tr>
                    <tr><td>Room charge (%s days)</td><td>₹%s</td></tr>
                    </table>
                    <h3>Room Services</h3>
                    <table>
                    <tr><th>Service</th><th>Quantity</th><th>Price</th><th>Total</th></tr>
                    %s
                    </table>
                    <div class="total">
                    Room Charges: ₹%s<br>
                    Service Charges: ₹%s<br>
                    Grand Total: ₹%s
                    </div>
                    <div class="actions">
                    <button class="print" onclick="window.print()">Print</button>
                    <button class="pdf" onclick="window.location.href='/invoice-pdf?bookingId=%s'">Save as PDF</button>
                    </div>
                    </div>
                    </body>
                    </html>
                    """.formatted(
                        bookingId,
                        htmlEscape(customerName),
                        htmlEscape(phone),
                        roomNumber,
                        floorNumber,
                        htmlEscape(roomType),
                        money(roomPrice),
                        days,
                        htmlEscape(bookingDate),
                        checkoutTimestamp == null ? "Not checked out" : htmlEscape(String.valueOf(checkoutTimestamp)),
                        htmlEscape(status),
                        days,
                        money(roomTotal),
                        serviceRows,
                        money(roomTotal),
                        money(serviceTotal),
                        money(grandTotal),
                        bookingId
                    );

                sendHtml(exchange, html);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to generate invoice.");
        }
    }


    // =========================================================
    // INVOICE PDF
    // =========================================================

    private static void invoicePdf(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> query = queryParameters(exchange);

        int bookingId;

        try {
            bookingId = Integer.parseInt(query.getOrDefault("bookingId", "0"));
        } catch (NumberFormatException e) {
            sendError(exchange, "Invalid booking ID.");
            return;
        }

        String sql = """
            SELECT b.booking_id, c.name, c.phone, b.room_number,
                   r.room_type, b.days, b.total, b.status,
                   b.booking_date, b.checkout_date
            FROM bookings b
            JOIN customers c
              ON c.hotel_id=b.hotel_id AND c.customer_id=b.customer_id
            JOIN rooms r
              ON r.hotel_id=b.hotel_id AND r.room_number=b.room_number
            WHERE b.hotel_id=? AND b.booking_id=?
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, hotelId);
            ps.setInt(2, bookingId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendError(exchange, "Invoice not found.");
                    return;
                }

                String customer = rs.getString("name");
                String phone = rs.getString("phone");
                int room = rs.getInt("room_number");
                String roomType = rs.getString("room_type");
                int days = rs.getInt("days");
                double roomTotal = rs.getDouble("total");
                String status = rs.getString("status");
                String bookingDate = String.valueOf(rs.getTimestamp("booking_date"));
                Timestamp checkoutDate = rs.getTimestamp("checkout_date");

                StringBuilder pdfText = new StringBuilder();

                pdfText.append("GRAND STAY HOTEL\n");
                pdfText.append("INVOICE\n\n");
                pdfText.append("Booking ID: ").append(bookingId).append("\n");
                pdfText.append("Customer: ").append(customer).append("\n");
                pdfText.append("Phone: ").append(phone).append("\n");
                pdfText.append("Room: ").append(room).append("\n");
                pdfText.append("Room Type: ").append(roomType).append("\n");
                pdfText.append("Days: ").append(days).append("\n");
                pdfText.append("Booking Date: ").append(bookingDate).append("\n");
                pdfText.append("Checkout Date: ")
                    .append(checkoutDate == null ? "Not checked out" : checkoutDate)
                    .append("\n");
                pdfText.append("Status: ").append(status).append("\n\n");
                pdfText.append("ROOM CHARGES\n");
                pdfText.append("Room total: Rs. ").append(money(roomTotal)).append("\n\n");

                double serviceTotal = 0;

                String serviceSql = """
                    SELECT service_name, quantity, price, total
                    FROM room_services
                    WHERE hotel_id=? AND booking_id=?
                    ORDER BY service_id
                    """;

                try (PreparedStatement servicePs = con.prepareStatement(serviceSql)) {
                    servicePs.setInt(1, hotelId);
                    servicePs.setInt(2, bookingId);

                    try (ResultSet serviceRs = servicePs.executeQuery()) {
                        pdfText.append("ROOM SERVICES\n");

                        while (serviceRs.next()) {
                            serviceTotal += serviceRs.getDouble("total");

                            pdfText.append(serviceRs.getString("service_name"))
                                .append(" x ")
                                .append(serviceRs.getInt("quantity"))
                                .append(" = Rs. ")
                                .append(money(serviceRs.getDouble("total")))
                                .append("\n");
                        }
                    }
                }

                pdfText.append("\nService total: Rs. ").append(money(serviceTotal)).append("\n");
                pdfText.append("GRAND TOTAL: Rs. ").append(money(roomTotal + serviceTotal)).append("\n");

                byte[] pdf = makePdf(pdfText.toString());

                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.getResponseHeaders().set(
                    "Content-Disposition",
                    "attachment; filename=\"Invoice-" + bookingId + ".pdf\""
                );

                exchange.sendResponseHeaders(200, pdf.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(pdf);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to create PDF invoice.");
        }
    }


    private static byte[] makePdf(String text) throws IOException {
        String[] lines = text.split("\n");

        StringBuilder content = new StringBuilder();
        content.append("BT\n/F1 12 Tf\n50 790 Td\n");

        boolean first = true;

        for (String line : lines) {
            String safeLine = line
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");

            if (!first) {
                content.append("0 -18 Td\n");
            }

            first = false;

            content.append("(")
                .append(safeLine)
                .append(") Tj\n");
        }

        content.append("ET");

        byte[] streamBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        String[] objects = new String[6];

        objects[1] = "<< /Type /Catalog /Pages 2 0 R >>";
        objects[2] = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>";
        objects[3] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>";
        objects[4] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>";
        objects[5] = "<< /Length " + streamBytes.length + " >>\nstream\n"
            + content + "\nendstream";

        output.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));

        int[] offsets = new int[6];

        for (int i = 1; i <= 5; i++) {
            offsets[i] = output.size();

            output.write((i + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
            output.write(objects[i].getBytes(StandardCharsets.ISO_8859_1));
            output.write("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }

        int xrefOffset = output.size();

        output.write("xref\n0 6\n".getBytes(StandardCharsets.ISO_8859_1));
        output.write("0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));

        for (int i = 1; i <= 5; i++) {
            output.write(String.format("%010d 00000 n \n", offsets[i])
                .getBytes(StandardCharsets.ISO_8859_1));
        }

        output.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"
            + xrefOffset + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1));

        return output.toByteArray();
    }


    // =========================================================
    // SETTINGS - PRICING DATA
    // =========================================================

    private static void pricingData(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            StringBuilder roomTypes = new StringBuilder("[");
            StringBuilder services = new StringBuilder("[");
            StringBuilder floors = new StringBuilder("[");

            boolean first = true;

            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT room_type, price
                    FROM room_types
                    WHERE hotel_id=?
                    ORDER BY room_type
                    """)) {

                ps.setInt(1, hotelId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (!first) {
                            roomTypes.append(",");
                        }

                        first = false;

                        roomTypes.append("{")
                            .append("\"roomType\":\"").append(jsonEscape(rs.getString("room_type"))).append("\",")
                            .append("\"price\":").append(rs.getDouble("price"))
                            .append("}");
                    }
                }
            }

            first = true;

            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT service_name, price
                    FROM service_prices
                    WHERE hotel_id=?
                    ORDER BY service_name
                    """)) {

                ps.setInt(1, hotelId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (!first) {
                            services.append(",");
                        }

                        first = false;

                        services.append("{")
                            .append("\"serviceName\":\"").append(jsonEscape(rs.getString("service_name"))).append("\",")
                            .append("\"price\":").append(rs.getDouble("price"))
                            .append("}");
                    }
                }
            }

            first = true;

            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT floor_number, floor_name
                    FROM hotel_floors
                    WHERE hotel_id=?
                    ORDER BY floor_number
                    """)) {

                ps.setInt(1, hotelId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (!first) {
                            floors.append(",");
                        }

                        first = false;

                        floors.append("{")
                            .append("\"floorNumber\":").append(rs.getInt("floor_number")).append(",")
                            .append("\"floorName\":\"").append(jsonEscape(rs.getString("floor_name"))).append("\"")
                            .append("}");
                    }
                }
            }

            roomTypes.append("]");
            services.append("]");
            floors.append("]");

            sendJson(exchange,
                "{\"roomTypes\":" + roomTypes
                + ",\"services\":" + services
                + ",\"floors\":" + floors + "}");

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(exchange, "Unable to load settings.");
        }
    }


    // =========================================================
    // UPDATE ROOM PRICES
    // =========================================================

    private static void updateRoomPrices(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> form = readForm(exchange);

        String roomType = form.getOrDefault("roomType", "").trim();

        double price;

        try {
            price = Double.parseDouble(form.getOrDefault("price", ""));
        } catch (NumberFormatException e) {
            sendError(exchange, "Enter a valid room price.");
            return;
        }

        if (roomType.isBlank() || price <= 0) {
            sendError(exchange, "Room type and a positive price are required.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                int updated;

                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE room_types
                        SET price=?
                        WHERE hotel_id=? AND room_type=?
                        """)) {

                    ps.setDouble(1, price);
                    ps.setInt(2, hotelId);
                    ps.setString(3, roomType);
                    updated = ps.executeUpdate();
                }

                if (updated == 0) {
                    con.rollback();
                    sendError(exchange, "Room type not found.");
                    return;
                }

                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE rooms
                        SET price=?
                        WHERE hotel_id=? AND room_type=?
                        """)) {

                    ps.setDouble(1, price);
                    ps.setInt(2, hotelId);
                    ps.setString(3, roomType);
                    ps.executeUpdate();
                }

                con.commit();

                sendJson(exchange,
                    "{\"success\":true,\"message\":\"Room price updated successfully.\"}");

            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to update room price.");
        }
    }


    // =========================================================
    // UPDATE SERVICE PRICES
    // =========================================================

    private static void updateServicePrice(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> form = readForm(exchange);

        String serviceName = form.getOrDefault("serviceName", "").trim();

        double price;

        try {
            price = Double.parseDouble(form.getOrDefault("price", ""));
        } catch (NumberFormatException e) {
            sendError(exchange, "Enter a valid service price.");
            return;
        }

        if (serviceName.isBlank() || price <= 0) {
            sendError(exchange, "Service name and a positive price are required.");
            return;
        }

        String sql = """
            UPDATE service_prices
            SET price=?
            WHERE hotel_id=? AND service_name=?
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDouble(1, price);
            ps.setInt(2, hotelId);
            ps.setString(3, serviceName);

            if (ps.executeUpdate() == 0) {
                sendError(exchange, "Service not found.");
                return;
            }

            sendJson(exchange,
                "{\"success\":true,\"message\":\"Service price updated successfully.\"}");

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(exchange, "Unable to update service price.");
        }
    }


    // =========================================================
    // ADD FLOOR
    // Creates one room for each configured room type.
    // =========================================================

    private static void addFloor(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> form = readForm(exchange);

        int floorNumber = 0;
        String floorName = form.getOrDefault("floorName", "").trim();

        try {
            floorNumber = Integer.parseInt(form.getOrDefault("floorNumber", "0"));
        } catch (NumberFormatException e) {
            sendError(exchange, "Enter a valid floor number.");
            return;
        }

        if (floorNumber <= 0 || floorName.isBlank()) {
            sendError(exchange, "Floor number and floor name are required.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO hotel_floors(hotel_id, floor_number, floor_name)
                        VALUES (?, ?, ?)
                        """)) {

                    ps.setInt(1, hotelId);
                    ps.setInt(2, floorNumber);
                    ps.setString(3, floorName);
                    ps.executeUpdate();
                }

                int roomsCreated = 0;

                String roomTypeSql = """
                    SELECT room_type, price
                    FROM room_types
                    WHERE hotel_id=?
                    ORDER BY room_type
                    """;

                try (PreparedStatement typePs = con.prepareStatement(roomTypeSql)) {
                    typePs.setInt(1, hotelId);

                    try (ResultSet rs = typePs.executeQuery()) {
                        while (rs.next()) {
                            String roomType = rs.getString("room_type");
                            double price = rs.getDouble("price");

                            int roomNumber = (floorNumber * 100) + roomsCreated + 1;

                            try (PreparedStatement roomPs = con.prepareStatement("""
                                    INSERT INTO rooms
                                    (room_number, floor_number, room_type, price, available, hotel_id)
                                    VALUES (?, ?, ?, ?, 1, ?)
                                    """)) {

                                roomPs.setInt(1, roomNumber);
                                roomPs.setInt(2, floorNumber);
                                roomPs.setString(3, roomType);
                                roomPs.setDouble(4, price);
                                roomPs.setInt(5, hotelId);

                                roomPs.executeUpdate();
                            }

                            roomsCreated++;
                        }
                    }
                }

                if (roomsCreated == 0) {
                    throw new SQLException("No room types are configured for this hotel.");
                }

                con.commit();

                sendJson(exchange,
                    "{\"success\":true,"
                    + "\"message\":\"Floor and rooms created successfully.\","
                    + "\"roomsCreated\":" + roomsCreated + "}");

            } catch (Exception e) {
                con.rollback();

                if (e instanceof SQLException
                        && e.getMessage() != null
                        && e.getMessage().contains("Duplicate")) {
                    sendError(exchange, "This floor number already exists.");
                    return;
                }

                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to add floor. " + e.getMessage());
        }
    }


    // =========================================================
    // DELETE FLOOR
    // Do not delete floors that have any booking history.
    // =========================================================

    private static void deleteFloor(HttpExchange exchange) throws IOException {
        int hotelId = getHotelId(exchange);

        if (hotelId == -1) {
            sendError(exchange, "Please log in again.");
            return;
        }

        Map<String, String> form = readForm(exchange);

        int floorNumber;

        try {
            floorNumber = Integer.parseInt(form.getOrDefault("floorNumber", "0"));
        } catch (NumberFormatException e) {
            sendError(exchange, "Invalid floor number.");
            return;
        }

        if (floorNumber <= 0) {
            sendError(exchange, "Invalid floor number.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                boolean floorExists = false;

                try (PreparedStatement ps = con.prepareStatement("""
                        SELECT floor_number
                        FROM hotel_floors
                        WHERE hotel_id=? AND floor_number=?
                        FOR UPDATE
                        """)) {

                    ps.setInt(1, hotelId);
                    ps.setInt(2, floorNumber);

                    try (ResultSet rs = ps.executeQuery()) {
                        floorExists = rs.next();
                    }
                }

                if (!floorExists) {
                    con.rollback();
                    sendError(exchange, "Floor not found.");
                    return;
                }

                int bookingCount;

                String historySql = """
                    SELECT COUNT(*)
                    FROM bookings b
                    JOIN rooms r
                      ON r.hotel_id=b.hotel_id
                     AND r.room_number=b.room_number
                    WHERE b.hotel_id=? AND r.floor_number=?
                    """;

                try (PreparedStatement ps = con.prepareStatement(historySql)) {
                    ps.setInt(1, hotelId);
                    ps.setInt(2, floorNumber);

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        bookingCount = rs.getInt(1);
                    }
                }

                if (bookingCount > 0) {
                    con.rollback();

                    sendError(exchange,
                        "This floor has booking history. Floors with active, checked-out or cancelled bookings cannot be deleted.");

                    return;
                }

                try (PreparedStatement ps = con.prepareStatement("""
                        DELETE FROM rooms
                        WHERE hotel_id=? AND floor_number=?
                        """)) {

                    ps.setInt(1, hotelId);
                    ps.setInt(2, floorNumber);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement("""
                        DELETE FROM hotel_floors
                        WHERE hotel_id=? AND floor_number=?
                        """)) {

                    ps.setInt(1, hotelId);
                    ps.setInt(2, floorNumber);
                    ps.executeUpdate();
                }

                con.commit();

                sendJson(exchange,
                    "{\"success\":true,\"message\":\"Floor deleted successfully.\"}");

            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, "Unable to delete floor.");
        }
    }


    // =========================================================
    // DATABASE HELPERS
    // =========================================================

    private static int scalarInt(Connection con, String sql, int hotelId)
            throws SQLException {

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, hotelId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }


    private static double scalarDouble(Connection con, String sql, int hotelId)
            throws SQLException {

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, hotelId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }

        return 0;
    }


    // =========================================================
    // HTTP AND FORM HELPERS
    // =========================================================

    private static boolean isMethod(HttpExchange exchange, String method) {
        return method.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String body) {
        return parseParameters(body);
    }

    private static String value(Map<String, String> data, String key) {
        return data.getOrDefault(key, "").trim();
    }

    private static Map<String, String> readForm(HttpExchange exchange)
            throws IOException {

        String body = new String(
            exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );

        return parseParameters(body);
    }


    private static Map<String, String> queryParameters(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();

        if (query == null || query.isBlank()) {
            return new HashMap<>();
        }

        return parseParameters(query);
    }


    private static Map<String, String> parseParameters(String input) {
        Map<String, String> result = new HashMap<>();

        if (input == null || input.isBlank()) {
            return result;
        }

        for (String pair : input.split("&")) {
            String[] parts = pair.split("=", 2);

            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1
                ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                : "";

            result.put(key, value);
        }

        return result;
    }


    private static void sendJson(HttpExchange exchange, String json)
            throws IOException {

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }


    private static void sendHtml(HttpExchange exchange, String html)
            throws IOException {

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type",
            "text/html; charset=UTF-8"
        );

        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }


    private static void sendError(HttpExchange exchange, String message)
            throws IOException {

        String json = "{\"success\":false,\"message\":\""
            + jsonEscape(message) + "\"}";

        sendJson(exchange, json);
    }


    private static void redirect(HttpExchange exchange, String location)
            throws IOException {

        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }


    private static void messagePage(HttpExchange exchange, String title, String message)
            throws IOException {

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <title>%s</title>
            <style>
            body{font-family:Arial;background:#f4f5f7;padding:50px}
            .box{max-width:550px;margin:auto;background:white;padding:30px;border-radius:12px}
            a{color:#167c59}
            </style>
            </head>
            <body>
            <div class="box">
            <h2>%s</h2>
            <p>%s</p>
            <a href="/">Return to home</a>
            </div>
            </body>
            </html>
            """.formatted(
                htmlEscape(title),
                htmlEscape(title),
                htmlEscape(message)
            );

        sendHtml(exchange, html);
    }


    // =========================================================
    // STATIC FILE SERVING
    // =========================================================

    private static void serveFile(HttpExchange exchange, String relativePath)
            throws IOException {

        Path base = Paths.get("webapp").toAbsolutePath().normalize();
        Path file = base.resolve(relativePath).normalize();

        if (!file.startsWith(base) || !Files.exists(file) || Files.isDirectory(file)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String contentType = "application/octet-stream";
        String lower = file.getFileName().toString().toLowerCase();

        if (lower.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (lower.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (lower.endsWith(".js")) {
            contentType = "application/javascript; charset=UTF-8";
        } else if (lower.endsWith(".png")) {
            contentType = "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (lower.endsWith(".svg")) {
            contentType = "image/svg+xml";
        }

        byte[] bytes = Files.readAllBytes(file);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }


    // =========================================================
    // FORMATTING AND ESCAPING
    // =========================================================

    private static String money(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }


    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }


    private static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

}