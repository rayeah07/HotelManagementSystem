package com.hotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class HotelServer {

    private static final int HOTEL_ID = 1;

    private static final Path WEB =
            Paths.get("webapp").toAbsolutePath().normalize();

    private static final int PASSWORD_ITERATIONS = 120000;
    private static final int PASSWORD_KEY_LENGTH = 256;
    private static final int PASSWORD_SALT_LENGTH = 16;

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private static byte[] generateSalt() {
        byte[] salt = new byte[PASSWORD_SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    private static String hashPassword(String password, byte[] salt)
            throws Exception {

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                PASSWORD_ITERATIONS,
                PASSWORD_KEY_LENGTH
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(
                            "PBKDF2WithHmacSHA256"
                    );

            byte[] hash = factory.generateSecret(spec).getEncoded();

            return Base64.getEncoder().encodeToString(hash);

        } finally {
            spec.clearPassword();
        }
    }

    private static boolean verifyPassword(
            String password,
            String storedHash,
            String storedSalt) {

        if (password == null ||
                storedHash == null ||
                storedSalt == null) {
            return false;
        }

        try {
            byte[] salt = Base64.getDecoder().decode(storedSalt);

            String generatedHash = hashPassword(password, salt);

            byte[] expected =
                    Base64.getDecoder().decode(storedHash);

            byte[] actual =
                    Base64.getDecoder().decode(generatedHash);

            return MessageDigest.isEqual(expected, actual);

        } catch (Exception e) {
            return false;
        }
    }

    // ==================================================
    // SERVER STARTUP
    // ==================================================

    public static void main(String[] args) throws Exception {

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(8080),
                        0
                );

        registerRoutes(server);

        server.setExecutor(null);
        server.start();

        System.out.println();
        System.out.println("==========================================");
        System.out.println("             GRAND STAY HOTEL");
        System.out.println("==========================================");
        System.out.println("Server running at:");
        System.out.println("http://localhost:8080");
        System.out.println();
        System.out.println("Hotel ID: " + HOTEL_ID);
        System.out.println("Press Ctrl+C to stop the server.");
        System.out.println("==========================================");
    }

    // ==================================================
    // ROUTE REGISTRATION
    // ==================================================

    private static void registerRoutes(HttpServer server) {

        server.createContext("/", exchange ->
                serveFile(exchange, "index.html"));

        server.createContext("/dashboard.html", exchange ->
                serveFile(exchange, "dashboard.html"));

        server.createContext("/rooms.html", exchange ->
                serveFile(exchange, "rooms.html"));

        server.createContext("/booking.html", exchange ->
                serveFile(exchange, "booking.html"));

        server.createContext("/bookings.html", exchange ->
                serveFile(exchange, "bookings.html"));

        server.createContext("/settings.html", exchange ->
                serveFile(exchange, "settings.html"));

        server.createContext("/css/style.css", exchange ->
                serveFile(exchange, "css/style.css"));

        server.createContext("/login", HotelServer::login);

        server.createContext("/dashboard-data",
                HotelServer::dashboardData);

        server.createContext("/all-rooms",
                HotelServer::allRooms);

        server.createContext("/available-rooms",
                HotelServer::availableRooms);

        server.createContext("/booking-history",
                HotelServer::bookingHistory);

        server.createContext("/book",
                HotelServer::book);

        server.createContext("/cancel-booking",
                HotelServer::cancelBooking);

        server.createContext("/checkout",
                HotelServer::checkout);

        server.createContext("/room-service",
                HotelServer::roomService);

        server.createContext("/service-list",
                HotelServer::serviceList);

        server.createContext("/invoice",
                HotelServer::invoice);

        server.createContext("/invoice-pdf",
                HotelServer::invoicePdf);

        server.createContext("/add-floor",
                HotelServer::addFloor);

        server.createContext("/delete-floor",
                HotelServer::deleteFloor);

        server.createContext("/add-rooms",
                HotelServer::addRooms);

        server.createContext("/add-rooms-every-floor",
                HotelServer::addRoomsEveryFloor);

        server.createContext("/pricing-data",
                HotelServer::pricingData);

        server.createContext("/update-room-prices",
                HotelServer::updateRoomPrices);

        server.createContext("/rename-room-type",
                HotelServer::renameRoomType);

        server.createContext("/add-service",
                HotelServer::addService);

        server.createContext("/update-service-price",
                HotelServer::updateServicePrice);

        server.createContext("/rename-service",
                HotelServer::renameService);

        server.createContext("/delete-service",
                HotelServer::deleteService);

        server.createContext("/change-password",
                HotelServer::changePassword);

        server.createContext("/hotel-info",
                HotelServer::hotelInfo);
    }

    // ==================================================
    // COMMON REQUEST HELPERS
    // ==================================================

    private static String readBody(HttpExchange exchange)
            throws IOException {

        try (var input = exchange.getRequestBody()) {

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseForm(String body) {

        Map<String, String> data = new LinkedHashMap<>();

        if (body == null || body.isBlank()) {
            return data;
        }

        for (String pair : body.split("&")) {

            String[] parts = pair.split("=", 2);

            String key = decode(parts[0]);

            String value = parts.length > 1
                    ? decode(parts[1])
                    : "";

            data.put(key, value);
        }

        return data;
    }

    private static String decode(String value) {

        try {
            return URLDecoder.decode(
                    value,
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            return value;
        }
    }

    private static String urlEncode(String value) {

        return URLEncoder.encode(
                value == null ? "" : value,
                StandardCharsets.UTF_8
        );
    }

    private static int intValue(String value, int fallback) {

        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double doubleValue(
            String value,
            double fallback) {

        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String jsonEscape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String formatDate(Timestamp timestamp) {

        if (timestamp == null) {
            return "";
        }

        return new SimpleDateFormat(
                "dd-MM-yyyy HH:mm",
                Locale.ENGLISH
        ).format(timestamp);
    }

    // ==================================================
    // COMMON RESPONSE HELPERS
    // ==================================================

    private static void sendResponse(
            HttpExchange exchange,
            String contentType,
            String body,
            int status) throws IOException {

        byte[] bytes =
                body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                contentType + "; charset=UTF-8"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sendJson(
            HttpExchange exchange,
            String json) throws IOException {

        sendResponse(exchange, "application/json", json, 200);
    }

    private static void sendHtml(
            HttpExchange exchange,
            String html) throws IOException {

        sendResponse(exchange, "text/html", html, 200);
    }

    private static void sendError(
            HttpExchange exchange,
            String message) throws IOException {

        sendJson(exchange,
                "{\"success\":false,\"message\":\""
                        + jsonEscape(message) + "\"}");
    }

    private static void sendSuccess(
            HttpExchange exchange,
            String message) throws IOException {

        sendJson(exchange,
                "{\"success\":true,\"message\":\""
                        + jsonEscape(message) + "\"}");
    }

    private static void redirect(
            HttpExchange exchange,
            String location) throws IOException {

        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    // ==================================================
    // FILE SERVING
    // ==================================================

    private static void serveFile(
            HttpExchange exchange,
            String fileName) throws IOException {

        Path file = WEB.resolve(fileName).normalize();

        if (!file.startsWith(WEB) ||
                !Files.exists(file) ||
                !Files.isRegularFile(file)) {

            sendResponse(
                    exchange,
                    "text/plain",
                    "File not found: " + fileName,
                    404
            );
            return;
        }

        String contentType;

        if (fileName.endsWith(".css")) {
            contentType = "text/css";
        } else if (fileName.endsWith(".js")) {
            contentType = "application/javascript";
        } else {
            contentType = "text/html";
        }

        byte[] bytes = Files.readAllBytes(file);

        exchange.getResponseHeaders().set(
                "Content-Type",
                contentType + "; charset=UTF-8"
        );

        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    // ==================================================
    // DATABASE HELPERS
    // ==================================================

    private static int scalarInt(
            Connection con,
            String query) throws SQLException {

        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static double scalarDouble(
            Connection con,
            String query) throws SQLException {

        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    private static boolean isPost(HttpExchange exchange) {
        return "POST".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static boolean isGet(HttpExchange exchange) {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static String getQueryValue(
            HttpExchange exchange,
            String key) {

        Map<String, String> query =
                parseForm(exchange.getRequestURI().getRawQuery());

        return query.getOrDefault(key, "");
    }

    // ==================================================
    // LOGIN - PBKDF2 PASSWORD VERIFICATION
    // ==================================================

    private static void login(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            redirect(exchange, "/");
            return;
        }

        Map<String, String> data =
                parseForm(readBody(exchange));

        String username =
                data.getOrDefault("username", "").trim();

        String password =
                data.getOrDefault("password", "");
        if (username.isEmpty() || password.isEmpty()) {
            sendHtml(exchange, messagePage(
                    "Login Failed",
                    "Please enter your username and password.",
                    "/",
                    "Try Again"
            ));
            return;
        }

        String sql =
                "SELECT hotel_id,password_hash,password_salt " +
                "FROM hotels WHERE username=?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    int hotelId = rs.getInt("hotel_id");

                    boolean validPassword = verifyPassword(
                            password,
                            rs.getString("password_hash"),
                            rs.getString("password_salt")
                    );

                    if (validPassword && hotelId == HOTEL_ID) {
                        redirect(exchange, "/dashboard.html");
                        return;
                    }
                }

                sendHtml(exchange, messagePage(
                        "Login Failed",
                        "Invalid username or password.",
                        "/",
                        "Try Again"
                ));
            }

        } catch (Exception e) {

            e.printStackTrace();

            sendHtml(exchange, messagePage(
                    "Login Error",
                    "Unable to process login. Please check the database connection.",
                    "/",
                    "Back"
            ));
        }
    }

    // ==================================================
    // DASHBOARD DATA
    // ==================================================

    private static void dashboardData(HttpExchange exchange)
            throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        String sql =
                "SELECT " +
                "(SELECT COUNT(*) FROM rooms WHERE hotel_id=?) AS total_rooms, " +
                "(SELECT COUNT(*) FROM rooms WHERE hotel_id=? AND available=TRUE) AS available_rooms, " +
                "(SELECT COUNT(*) FROM bookings WHERE hotel_id=?) AS total_bookings, " +
                "(SELECT COALESCE(SUM(total),0) FROM bookings WHERE hotel_id=? AND status<>'CANCELLED') AS room_revenue, " +
                "(SELECT COALESCE(SUM(total),0) FROM room_services WHERE hotel_id=?) AS service_revenue";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            for (int i = 1; i <= 5; i++) {
                ps.setInt(i, HOTEL_ID);
            }

            try (ResultSet rs = ps.executeQuery()) {

                rs.next();

                int totalRooms = rs.getInt("total_rooms");
                int availableRooms = rs.getInt("available_rooms");
                int totalBookings = rs.getInt("total_bookings");

                double revenue =
                        rs.getDouble("room_revenue")
                        + rs.getDouble("service_revenue");

                StringBuilder json = new StringBuilder();

                json.append("{")
                        .append("\"totalRooms\":").append(totalRooms).append(",")
                        .append("\"availableRooms\":").append(availableRooms).append(",")
                        .append("\"bookedRooms\":").append(totalRooms - availableRooms).append(",")
                        .append("\"totalBookings\":").append(totalBookings).append(",")
                        .append("\"revenue\":").append(revenue).append(",")
                        .append("\"rooms\":[");

                String roomSql =
                        "SELECT room_number,floor_number,room_type,price,available " +
                        "FROM rooms WHERE hotel_id=? ORDER BY room_number";

                try (PreparedStatement roomPs = con.prepareStatement(roomSql)) {

                    roomPs.setInt(1, HOTEL_ID);

                    try (ResultSet rooms = roomPs.executeQuery()) {

                        boolean first = true;

                        while (rooms.next()) {

                            if (!first) json.append(",");
                            first = false;

                            json.append("{")
                                    .append("\"roomNumber\":").append(rooms.getInt("room_number")).append(",")
                                    .append("\"floor\":").append(rooms.getInt("floor_number")).append(",")
                                    .append("\"roomType\":\"").append(jsonEscape(rooms.getString("room_type"))).append("\",")
                                    .append("\"price\":").append(rooms.getDouble("price")).append(",")
                                    .append("\"available\":").append(rooms.getBoolean("available"))
                                    .append("}");
                        }
                    }
                }

                json.append("],\"recentBookings\":[");

                String bookingSql =
                        "SELECT b.booking_id,c.name,b.room_number,r.room_type," +
                        "b.days,b.total,b.status,b.booking_date," +
                        "COALESCE((SELECT SUM(rs.total) FROM room_services rs " +
                        "WHERE rs.hotel_id=b.hotel_id AND rs.booking_id=b.booking_id),0) AS service_total " +
                        "FROM bookings b " +
                        "JOIN customers c ON c.hotel_id=b.hotel_id AND c.customer_id=b.customer_id " +
                        "JOIN rooms r ON r.hotel_id=b.hotel_id AND r.room_number=b.room_number " +
                        "WHERE b.hotel_id=? ORDER BY b.booking_id DESC LIMIT 5";

                try (PreparedStatement bookingPs = con.prepareStatement(bookingSql)) {

                    bookingPs.setInt(1, HOTEL_ID);

                    try (ResultSet bookings = bookingPs.executeQuery()) {

                        boolean first = true;

                        while (bookings.next()) {

                            if (!first) json.append(",");
                            first = false;

                            json.append("{")
                                    .append("\"bookingId\":").append(bookings.getInt("booking_id")).append(",")
                                    .append("\"name\":\"").append(jsonEscape(bookings.getString("name"))).append("\",")
                                    .append("\"room\":").append(bookings.getInt("room_number")).append(",")
                                    .append("\"roomType\":\"").append(jsonEscape(bookings.getString("room_type"))).append("\",")
                                    .append("\"days\":").append(bookings.getInt("days")).append(",")
                                    .append("\"total\":").append(bookings.getDouble("total")).append(",")
                                    .append("\"serviceTotal\":").append(bookings.getDouble("service_total")).append(",")
                                    .append("\"status\":\"").append(jsonEscape(bookings.getString("status"))).append("\",")
                                    .append("\"date\":\"").append(jsonEscape(formatDate(bookings.getTimestamp("booking_date")))).append("\"")
                                    .append("}");
                        }
                    }
                }

                json.append("]}");
                sendJson(exchange, json.toString());
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // ROOM LISTS
    // ==================================================

    private static void allRooms(HttpExchange exchange)
            throws IOException {
        roomList(exchange, false);
    }

    private static void availableRooms(HttpExchange exchange)
            throws IOException {
        roomList(exchange, true);
    }

    private static void roomList(
            HttpExchange exchange,
            boolean onlyAvailable) throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        String sql =
                "SELECT room_number,floor_number,room_type,price,available " +
                "FROM rooms WHERE hotel_id=? ";

        if (onlyAvailable) {
            sql += "AND available=TRUE ";
        }

        sql += "ORDER BY floor_number,room_number";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, HOTEL_ID);

            try (ResultSet rs = ps.executeQuery()) {

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {

                    if (!first) json.append(",");
                    first = false;

                    json.append("{")
                            .append("\"roomNumber\":").append(rs.getInt("room_number")).append(",")
                            .append("\"floor\":").append(rs.getInt("floor_number")).append(",")
                            .append("\"floorNumber\":").append(rs.getInt("floor_number")).append(",")
                            .append("\"roomType\":\"").append(jsonEscape(rs.getString("room_type"))).append("\",")
                            .append("\"price\":").append(rs.getDouble("price")).append(",")
                            .append("\"available\":").append(rs.getBoolean("available"))
                            .append("}");
                }

                json.append("]");
                sendJson(exchange, json.toString());
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // BOOKING HISTORY
    // ==================================================

    private static void bookingHistory(HttpExchange exchange)
            throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        String sql =
                "SELECT b.booking_id,c.name,c.phone,b.room_number," +
                "r.room_type,b.days,b.total,b.status,b.booking_date,b.checkout_date," +
                "COALESCE((SELECT SUM(rs.total) FROM room_services rs " +
                "WHERE rs.hotel_id=b.hotel_id AND rs.booking_id=b.booking_id),0) AS service_total " +
                "FROM bookings b " +
                "JOIN customers c ON c.hotel_id=b.hotel_id AND c.customer_id=b.customer_id " +
                "JOIN rooms r ON r.hotel_id=b.hotel_id AND r.room_number=b.room_number " +
                "WHERE b.hotel_id=? ORDER BY b.booking_id DESC";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, HOTEL_ID);

            try (ResultSet rs = ps.executeQuery()) {

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {

                    if (!first) json.append(",");
                    first = false;

                    json.append("{")
                            .append("\"bookingId\":").append(rs.getInt("booking_id")).append(",")
                            .append("\"name\":\"").append(jsonEscape(rs.getString("name"))).append("\",")
                            .append("\"phone\":\"").append(jsonEscape(rs.getString("phone"))).append("\",")
                            .append("\"roomNumber\":").append(rs.getInt("room_number")).append(",")
                            .append("\"roomType\":\"").append(jsonEscape(rs.getString("room_type"))).append("\",")
                            .append("\"days\":").append(rs.getInt("days")).append(",")
                            .append("\"total\":").append(rs.getDouble("total")).append(",")
                            .append("\"status\":\"").append(jsonEscape(rs.getString("status"))).append("\",")
                            .append("\"bookingDate\":\"").append(jsonEscape(formatDate(rs.getTimestamp("booking_date")))).append("\",")
                            .append("\"checkoutDate\":\"").append(jsonEscape(formatDate(rs.getTimestamp("checkout_date")))).append("\",")
                            .append("\"serviceTotal\":").append(rs.getDouble("service_total"))
                            .append("}");
                }

                json.append("]");
                sendJson(exchange, json.toString());
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // CREATE BOOKING
    // ==================================================

    private static void book(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        String name = data.getOrDefault("name", "").trim();
        String phone = data.getOrDefault("phone", "").trim();

        int room = intValue(
                data.getOrDefault("roomNumber",
                        data.getOrDefault("room", "0")), 0);

        int days = intValue(data.get("days"), 0);

        if (name.isBlank() || phone.isBlank() || room <= 0 || days <= 0) {
            sendError(exchange, "Please enter valid booking details.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                double price;

                String roomSql =
                        "SELECT price,available FROM rooms " +
                        "WHERE hotel_id=? AND room_number=? FOR UPDATE";

                try (PreparedStatement ps = con.prepareStatement(roomSql)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, room);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new IllegalArgumentException("Room does not exist.");
                        }

                        if (!rs.getBoolean("available")) {
                            throw new IllegalArgumentException("Room is already booked.");
                        }

                        price = rs.getDouble("price");
                    }
                }

                int customerId;

                String customerSql =
                        "INSERT INTO customers(name,phone,hotel_id) VALUES(?,?,?)";

                try (PreparedStatement ps = con.prepareStatement(
                        customerSql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, name);
                    ps.setString(2, phone);
                    ps.setInt(3, HOTEL_ID);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Could not create customer.");
                        }
                        customerId = keys.getInt(1);
                    }
                }

                double total = price * days;
                int bookingId;

                String bookingSql =
                        "INSERT INTO bookings " +
                        "(customer_id,room_number,days,total,status,hotel_id) " +
                        "VALUES(?,?,?,?, 'ACTIVE',?)";

                try (PreparedStatement ps = con.prepareStatement(
                        bookingSql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setInt(1, customerId);
                    ps.setInt(2, room);
                    ps.setInt(3, days);
                    ps.setDouble(4, total);
                    ps.setInt(5, HOTEL_ID);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Could not create booking.");
                        }
                        bookingId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE rooms SET available=FALSE " +
                        "WHERE hotel_id=? AND room_number=?")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, room);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Could not update room availability.");
                    }
                }

                con.commit();

                redirect(exchange, "/bookings.html?success=" +
                        urlEncode("Booking created successfully. Booking ID: " + bookingId));

            } catch (Exception e) {
                con.rollback();
                sendError(exchange, e.getMessage());
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // CANCEL BOOKING
    // ==================================================

    private static void cancelBooking(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));
        int bookingId = intValue(
                data.getOrDefault("booking_id",
                        data.getOrDefault("bookingId", "0")), 0);

        if (bookingId <= 0) {
            sendError(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                int roomNumber;

                String selectSql =
                        "SELECT room_number,status FROM bookings " +
                        "WHERE hotel_id=? AND booking_id=? FOR UPDATE";

                try (PreparedStatement ps = con.prepareStatement(selectSql)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new IllegalArgumentException("Booking not found.");
                        }

                        if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                            throw new IllegalArgumentException(
                                    "Only active bookings can be cancelled.");
                        }

                        roomNumber = rs.getInt("room_number");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bookings SET status='CANCELLED' " +
                        "WHERE hotel_id=? AND booking_id=?")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, bookingId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE rooms SET available=TRUE " +
                        "WHERE hotel_id=? AND room_number=?")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, roomNumber);
                    ps.executeUpdate();
                }

                con.commit();

                sendSuccess(exchange, "Booking cancelled successfully.");

            } catch (Exception e) {
                con.rollback();
                sendError(exchange, e.getMessage());
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // CHECKOUT
    // ==================================================

    private static void checkout(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));
        int bookingId = intValue(
                data.getOrDefault("booking_id",
                        data.getOrDefault("bookingId", "0")), 0);

        if (bookingId <= 0) {
            sendError(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                int roomNumber;

                String selectSql =
                        "SELECT room_number,status FROM bookings " +
                        "WHERE hotel_id=? AND booking_id=? FOR UPDATE";

                try (PreparedStatement ps = con.prepareStatement(selectSql)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new IllegalArgumentException("Booking not found.");
                        }

                        if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                            throw new IllegalArgumentException(
                                    "Only active bookings can be checked out.");
                        }

                        roomNumber = rs.getInt("room_number");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bookings SET status='CHECKED_OUT',checkout_date=NOW() " +
                        "WHERE hotel_id=? AND booking_id=?")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, bookingId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE rooms SET available=TRUE " +
                        "WHERE hotel_id=? AND room_number=?")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, roomNumber);
                    ps.executeUpdate();
                }

                con.commit();
                sendJson(exchange,
                        "{\"success\":true,\"message\":\"Checkout successful.\"," +
                        "\"invoiceUrl\":\"/invoice?booking_id=" + bookingId + "\"}");

            } catch (Exception e) {
                con.rollback();
                sendError(exchange, e.getMessage());
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // ROOM SERVICE
    // ==================================================

    private static void roomService(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        int bookingId = intValue(
                data.getOrDefault("booking_id",
                        data.getOrDefault("bookingId", "0")), 0);
        int quantity = intValue(data.get("quantity"), 1);

        String serviceName = data.getOrDefault(
                "service_name",
                data.getOrDefault("serviceName", "")
        ).trim();

        if (bookingId <= 0 || quantity <= 0 || serviceName.isBlank()) {
            sendError(exchange, "Please select a valid service and quantity.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                String status;

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT status FROM bookings " +
                        "WHERE hotel_id=? AND booking_id=? FOR UPDATE")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new IllegalArgumentException("Booking not found.");
                        }

                        status = rs.getString("status");
                    }
                }

                if (!"ACTIVE".equalsIgnoreCase(status)) {
                    throw new IllegalArgumentException(
                            "Services can only be added to active bookings.");
                }

                double price;

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT price FROM service_prices " +
                        "WHERE hotel_id=? AND service_name=?")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setString(2, serviceName);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "This service is not available.");
                        }

                        price = rs.getDouble("price");
                    }
                }

                double total = price * quantity;

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO room_services " +
                        "(booking_id,service_name,quantity,price,total,service_date,hotel_id) " +
                        "VALUES(?,?,?,?,?,NOW(),?)")) {

                    ps.setInt(1, bookingId);
                    ps.setString(2, serviceName);
                    ps.setInt(3, quantity);
                    ps.setDouble(4, price);
                    ps.setDouble(5, total);
                    ps.setInt(6, HOTEL_ID);
                    ps.executeUpdate();
                }

                con.commit();

                sendSuccess(exchange, "Service added successfully.");

            } catch (Exception e) {
                con.rollback();
                sendError(exchange, e.getMessage());
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // SERVICE LIST
    // ==================================================

    private static void serviceList(HttpExchange exchange)
            throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        int bookingId = intValue(
                getQueryValue(exchange, "booking_id"), 0);

        try (Connection con = DatabaseConnection.getConnection()) {

            if (bookingId > 0) {

                String sql =
                        "SELECT service_name,quantity,price,total,service_date " +
                        "FROM room_services WHERE hotel_id=? AND booking_id=? " +
                        "ORDER BY service_id DESC";

                try (PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {

                        StringBuilder json = new StringBuilder("[");
                        boolean first = true;

                        while (rs.next()) {

                            if (!first) json.append(",");
                            first = false;

                            json.append("{")
                                    .append("\"name\":\"").append(jsonEscape(rs.getString("service_name"))).append("\",")
                                    .append("\"quantity\":").append(rs.getInt("quantity")).append(",")
                                    .append("\"price\":").append(rs.getDouble("price")).append(",")
                                    .append("\"total\":").append(rs.getDouble("total")).append(",")
                                    .append("\"date\":\"").append(jsonEscape(formatDate(rs.getTimestamp("service_date")))).append("\"")
                                    .append("}");
                        }

                        json.append("]");
                        sendJson(exchange, json.toString());
                    }
                }

            } else {

                String sql =
                        "SELECT service_name,price FROM service_prices " +
                        "WHERE hotel_id=? ORDER BY service_name";

                try (PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setInt(1, HOTEL_ID);

                    try (ResultSet rs = ps.executeQuery()) {

                        StringBuilder json = new StringBuilder("[");
                        boolean first = true;

                        while (rs.next()) {

                            if (!first) json.append(",");
                            first = false;

                            json.append("{")
                                    .append("\"name\":\"").append(jsonEscape(rs.getString("service_name"))).append("\",")
                                    .append("\"serviceName\":\"").append(jsonEscape(rs.getString("service_name"))).append("\",")
                                    .append("\"price\":").append(rs.getDouble("price"))
                                    .append("}");
                        }

                        json.append("]");
                        sendJson(exchange, json.toString());
                    }
                }
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // INVOICE PAGE
    // ==================================================

    private static void invoice(HttpExchange exchange)
            throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        int bookingId = intValue(
                getQueryValue(exchange, "booking_id"), 0);

        if (bookingId <= 0) {
            sendError(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            String name;
            String phone;
            String roomType;
            String status;
            String bookingDate;
            String checkoutDate;

            int room;
            int days;

            double roomTotal;
            double serviceTotal;

            String sql =
                    "SELECT c.name,c.phone,b.room_number,r.room_type," +
                    "b.days,b.total,b.status,b.booking_date,b.checkout_date," +
                    "COALESCE((SELECT SUM(rs.total) FROM room_services rs " +
                    "WHERE rs.hotel_id=b.hotel_id AND rs.booking_id=b.booking_id),0) AS service_total " +
                    "FROM bookings b " +
                    "JOIN customers c ON c.hotel_id=b.hotel_id AND c.customer_id=b.customer_id " +
                    "JOIN rooms r ON r.hotel_id=b.hotel_id AND r.room_number=b.room_number " +
                    "WHERE b.hotel_id=? AND b.booking_id=?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setInt(1, HOTEL_ID);
                ps.setInt(2, bookingId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        sendError(exchange, "Booking not found.");
                        return;
                    }

                    name = rs.getString("name");
                    phone = rs.getString("phone");
                    room = rs.getInt("room_number");
                    roomType = rs.getString("room_type");
                    days = rs.getInt("days");
                    roomTotal = rs.getDouble("total");
                    status = rs.getString("status");
                    bookingDate = formatDate(rs.getTimestamp("booking_date"));
                    checkoutDate = formatDate(rs.getTimestamp("checkout_date"));
                    serviceTotal = rs.getDouble("service_total");
                }
            }

            double grandTotal = roomTotal + serviceTotal;
            StringBuilder serviceRows = new StringBuilder();

            String serviceSql =
                    "SELECT service_name,quantity,price,total " +
                    "FROM room_services WHERE hotel_id=? AND booking_id=? " +
                    "ORDER BY service_id";

            try (PreparedStatement ps = con.prepareStatement(serviceSql)) {

                ps.setInt(1, HOTEL_ID);
                ps.setInt(2, bookingId);

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {

                        serviceRows.append("<tr>")
                                .append("<td>").append(esc(rs.getString("service_name"))).append("</td>")
                                .append("<td>").append(rs.getInt("quantity")).append("</td>")
                                .append("<td>INR ").append(String.format(Locale.US, "%.2f", rs.getDouble("price"))).append("</td>")
                                .append("<td>INR ").append(String.format(Locale.US, "%.2f", rs.getDouble("total"))).append("</td>")
                                .append("</tr>");
                    }
                }
            }

            double nightlyRate = days == 0 ? 0 : roomTotal / days;

            String html =
                    "<!DOCTYPE html><html><head>" +
                    "<meta charset='UTF-8'>" +
                    "<title>Invoice #" + bookingId + "</title>" +
                    "<link rel='stylesheet' href='/css/style.css'>" +
                    "</head><body class='invoice-page'>" +
                    "<div class='invoice-card'>" +

                    "<div class='invoice-head'>" +
                    "<div><div class='invoice-logo'>GS</div>" +
                    "<h1>Grand Stay Hotel</h1><p>Guest Invoice</p></div>" +
                    "<div class='invoice-id'>Invoice #" + bookingId + "</div>" +
                    "</div>" +

                    "<div class='invoice-info'>" +
                    "<div><b>Guest</b><br>" + esc(name) + "<br>" + esc(phone) + "</div>" +
                    "<div><b>Room</b><br>" + room + " - " + esc(roomType) +
                    "<br>" + days + " night(s)</div>" +
                    "<div><b>Status</b><br>" + esc(status) + "<br>" +
                    esc(checkoutDate.isBlank() ? bookingDate : checkoutDate) +
                    "</div></div>" +

                    "<table class='invoice-table'>" +
                    "<thead><tr><th>Item</th><th>Qty</th><th>Price</th><th>Total</th></tr></thead>" +
                    "<tbody>" +

                    "<tr><td>Room stay</td><td>" + days + "</td>" +
                    "<td>INR " + String.format(Locale.US, "%.2f", nightlyRate) + "</td>" +
                    "<td>INR " + String.format(Locale.US, "%.2f", roomTotal) + "</td></tr>" +

                    serviceRows +

                    "</tbody></table>" +

                    "<div class='invoice-total'>" +
                    "<span>Room Total</span><b>INR " +
                    String.format(Locale.US, "%.2f", roomTotal) + "</b>" +
                    "<span>Services</span><b>INR " +
                    String.format(Locale.US, "%.2f", serviceTotal) + "</b>" +
                    "<span>Grand Total</span><strong>INR " +
                    String.format(Locale.US, "%.2f", grandTotal) +
                    "</strong></div>" +

                    "<div class='invoice-actions'>" +
                    "<button class='btn primary' onclick='window.print()'>Print</button>" +
                    "<a class='btn secondary' href='/invoice-pdf?booking_id=" + bookingId + "'>Save as PDF</a>" +
                    "<a class='btn secondary' href='/bookings.html'>Back to History</a>" +
                    "</div></div></body></html>";

            sendHtml(exchange, html);

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // INVOICE PDF
    // ==================================================

    private static void invoicePdf(HttpExchange exchange)
            throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        int bookingId = intValue(
                getQueryValue(exchange, "booking_id"), 0);

        if (bookingId <= 0) {
            sendError(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            String name;
            String status;

            int room;
            int days;

            double roomTotal;
            double serviceTotal;

            String sql =
                    "SELECT c.name,b.room_number,b.days,b.total,b.status," +
                    "COALESCE((SELECT SUM(rs.total) FROM room_services rs " +
                    "WHERE rs.hotel_id=b.hotel_id AND rs.booking_id=b.booking_id),0) AS service_total " +
                    "FROM bookings b " +
                    "JOIN customers c ON c.hotel_id=b.hotel_id AND c.customer_id=b.customer_id " +
                    "WHERE b.hotel_id=? AND b.booking_id=?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setInt(1, HOTEL_ID);
                ps.setInt(2, bookingId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        sendError(exchange, "Booking not found.");
                        return;
                    }

                    name = rs.getString("name");
                    room = rs.getInt("room_number");
                    days = rs.getInt("days");
                    roomTotal = rs.getDouble("total");
                    status = rs.getString("status");
                    serviceTotal = rs.getDouble("service_total");
                }
            }

            double grandTotal = roomTotal + serviceTotal;

            List<String> lines = new ArrayList<>();

            lines.add("GRAND STAY HOTEL");
            lines.add("Guest Invoice #" + bookingId);
            lines.add("Guest: " + name);
            lines.add("Room: " + room);
            lines.add("Nights: " + days);
            lines.add("Status: " + status);
            lines.add("Room total: INR " + String.format(Locale.US, "%.2f\n", roomTotal));
            lines.add("Services: INR " + String.format(Locale.US, "%.2f\n", serviceTotal));
            lines.add("Grand total: INR " + String.format(Locale.US, "%.2f", grandTotal));

            byte[] pdf = simplePdf(lines);

            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.getResponseHeaders().set(
                    "Content-Disposition",
                    "attachment; filename=\"invoice-" + bookingId + ".pdf\""
            );

            exchange.sendResponseHeaders(200, pdf.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(pdf);
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // HTML ESCAPING
    // ==================================================

    private static String esc(String value) {

        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ==================================================
    // SIMPLE PDF GENERATOR
    // ==================================================

    private static byte[] simplePdf(List<String> lines)
            throws IOException {

        StringBuilder stream = new StringBuilder();

        stream.append("BT /F1 14 Tf 50 790 Td ");

        for (int i = 0; i < lines.size(); i++) {

            if (i > 0) stream.append("0 -28 Td ");

            String safe = lines.get(i)
                    .replace("\\", "\\\\")
                    .replace("(", "\\(")
                    .replace(")", "\\)");

            stream.append("(").append(safe).append(") Tj ");
        }

        stream.append("ET");

        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + stream.length() + " >>\nstream\n" +
                stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        };

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));

        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);

        for (int i = 0; i < objects.length; i++) {

            offsets.add(output.size());

            output.write(
                    ((i + 1) + " 0 obj\n" +
                    objects[i] + "\nendobj\n")
                            .getBytes(StandardCharsets.US_ASCII)
            );
        }

        int xref = output.size();

        output.write(
                ("xref\n0 " + (objects.length + 1) +
                "\n0000000000 65535 f \n")
                        .getBytes(StandardCharsets.US_ASCII)
        );

        for (int i = 1; i < offsets.size(); i++) {

            output.write(
                    String.format(
                            Locale.US,
                            "%010d 00000 n \n",
                            offsets.get(i)
                    ).getBytes(StandardCharsets.US_ASCII)
            );
        }

        output.write(
                ("trailer\n<< /Size " + (objects.length + 1) +
                " /Root 1 0 R >>\nstartxref\n" +
                xref + "\n%%EOF")
                        .getBytes(StandardCharsets.US_ASCII)
        );

        return output.toByteArray();
    }

    // ==================================================
    // PRICING DATA
    // ==================================================

    private static void pricingData(HttpExchange exchange)
            throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            StringBuilder json = new StringBuilder("{");

            json.append("\"roomTypes\":[");

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT room_type,price FROM room_types " +
                    "WHERE hotel_id=? ORDER BY room_type")) {

                ps.setInt(1, HOTEL_ID);

                try (ResultSet rs = ps.executeQuery()) {

                    boolean first = true;

                    while (rs.next()) {

                        if (!first) json.append(",");
                        first = false;

                        json.append("{\"roomType\":\"")
                                .append(jsonEscape(rs.getString("room_type")))
                                .append("\",\"name\":\"")
                                .append(jsonEscape(rs.getString("room_type")))
                                .append("\",\"price\":")
                                .append(rs.getDouble("price"))
                                .append("}");
                    }
                }
            }

            json.append("],\"floors\":[");

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT floor_number,MAX(room_type) AS room_type " +
                    "FROM rooms WHERE hotel_id=? " +
                    "GROUP BY floor_number ORDER BY floor_number")) {

                ps.setInt(1, HOTEL_ID);

                try (ResultSet rs = ps.executeQuery()) {

                    boolean first = true;

                    while (rs.next()) {

                        if (!first) json.append(",");
                        first = false;

                        json.append("{\"floorNumber\":")
                                .append(rs.getInt("floor_number"))
                                .append(",\"floor\":")
                                .append(rs.getInt("floor_number"))
                                .append(",\"roomType\":\"")
                                .append(jsonEscape(rs.getString("room_type")))
                                .append("\"}");
                    }
                }
            }

            json.append("],\"services\":[");

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT service_name,price FROM service_prices " +
                    "WHERE hotel_id=? ORDER BY service_name")) {

                ps.setInt(1, HOTEL_ID);

                try (ResultSet rs = ps.executeQuery()) {

                    boolean first = true;

                    while (rs.next()) {

                        if (!first) json.append(",");
                        first = false;

                        json.append("{\"serviceName\":\"")
                                .append(jsonEscape(rs.getString("service_name")))
                                .append("\",\"name\":\"")
                                .append(jsonEscape(rs.getString("service_name")))
                                .append("\",\"price\":")
                                .append(rs.getDouble("price"))
                                .append("}");
                    }
                }
            }

            json.append("]}");
            sendJson(exchange, json.toString());

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // UPDATE ROOM PRICES
    // ==================================================

    private static void updateRoomPrices(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        String roomType = data.getOrDefault("roomType", "").trim();
        double price = doubleValue(data.get("price"), -1);

        if (roomType.isBlank() || price < 0) {
            sendError(exchange, "Enter a valid room type and price.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE room_types SET price=? " +
                        "WHERE hotel_id=? AND room_type=?")) {

                    ps.setDouble(1, price);
                    ps.setInt(2, HOTEL_ID);
                    ps.setString(3, roomType);

                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Room type not found.");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE rooms SET price=? " +
                        "WHERE hotel_id=? AND room_type=?")) {

                    ps.setDouble(1, price);
                    ps.setInt(2, HOTEL_ID);
                    ps.setString(3, roomType);
                    ps.executeUpdate();
                }

                con.commit();
                sendSuccess(exchange, "Room price updated.");

            } catch (Exception e) {
                con.rollback();
                throw e;
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // RENAME ROOM TYPE
    // ==================================================

    private static void renameRoomType(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        String oldName =
                data.getOrDefault("oldRoomType", "").trim();

        String newName =
                data.getOrDefault("newRoomType", "").trim();

        if (oldName.isBlank() || newName.isBlank()) {
            sendError(exchange, "Enter both room type names.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE rooms SET room_type=? " +
                        "WHERE hotel_id=? AND room_type=?")) {

                    ps.setString(1, newName);
                    ps.setInt(2, HOTEL_ID);
                    ps.setString(3, oldName);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE room_types SET room_type=? " +
                        "WHERE hotel_id=? AND room_type=?")) {

                    ps.setString(1, newName);
                    ps.setInt(2, HOTEL_ID);
                    ps.setString(3, oldName);

                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Room type not found.");
                    }
                }

                con.commit();
                sendSuccess(exchange, "Room type renamed.");

            } catch (Exception e) {
                con.rollback();
                throw e;
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // ADD SERVICE
    // ==================================================

    private static void addService(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        String name = data.getOrDefault("serviceName", "").trim();
        double price = doubleValue(data.get("price"), -1);

        if (name.isBlank() || price < 0) {
            sendError(exchange, "Enter a valid service name and price.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO service_prices " +
                     "(hotel_id,service_name,price) VALUES(?,?,?)")) {

            ps.setInt(1, HOTEL_ID);
            ps.setString(2, name);
            ps.setDouble(3, price);
            ps.executeUpdate();

            sendSuccess(exchange, "Service added.");

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // UPDATE SERVICE PRICE
    // ==================================================

    private static void updateServicePrice(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        String name = data.getOrDefault("serviceName", "").trim();
        double price = doubleValue(data.get("price"), -1);

        if (name.isBlank() || price < 0) {
            sendError(exchange, "Enter a valid service and price.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE service_prices SET price=? " +
                     "WHERE hotel_id=? AND service_name=?")) {

            ps.setDouble(1, price);
            ps.setInt(2, HOTEL_ID);
            ps.setString(3, name);

            if (ps.executeUpdate() == 0) {
                sendError(exchange, "Service not found.");
                return;
            }

            sendSuccess(exchange, "Service price updated.");

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // RENAME SERVICE
    // ==================================================

    private static void renameService(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        String oldName =
                data.getOrDefault("oldServiceName", "").trim();

        String newName =
                data.getOrDefault("newServiceName", "").trim();

        if (oldName.isBlank() || newName.isBlank()) {
            sendError(exchange, "Enter both service names.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE service_prices SET service_name=? " +
                     "WHERE hotel_id=? AND service_name=?")) {

            ps.setString(1, newName);
            ps.setInt(2, HOTEL_ID);
            ps.setString(3, oldName);

            if (ps.executeUpdate() == 0) {
                sendError(exchange, "Service not found.");
                return;
            }

            sendSuccess(exchange, "Service renamed.");

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // DELETE SERVICE
    // ==================================================

    private static void deleteService(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        String name = data.getOrDefault("serviceName", "").trim();

        if (name.isBlank()) {
            sendError(exchange, "Select a service.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM service_prices " +
                     "WHERE hotel_id=? AND service_name=?")) {

            ps.setInt(1, HOTEL_ID);
            ps.setString(2, name);

            if (ps.executeUpdate() == 0) {
                sendError(exchange, "Service not found.");
                return;
            }

            sendSuccess(exchange, "Service deleted.");

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // CHANGE PASSWORD - DATABASE BACKED
    // ==================================================

    private static void changePassword(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        String oldPassword =
                data.getOrDefault("oldPassword", "");

        String newPassword =
                data.getOrDefault("newPassword", "");

        String confirmPassword =
                data.getOrDefault("confirmPassword", "");

        if (oldPassword.isEmpty() ||
                newPassword.isEmpty() ||
                confirmPassword.isEmpty()) {

            sendError(exchange, "Please fill in all password fields.");
            return;
        }

        if (newPassword.length() < 8) {
            sendError(exchange, "New password must contain at least 8 characters.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            sendError(exchange, "New passwords do not match.");
            return;
        }

        if (oldPassword.equals(newPassword)) {
            sendError(exchange, "New password must be different from the current password.");
            return;
        }

        String selectSQL =
                "SELECT password_hash,password_salt " +
                "FROM hotels WHERE hotel_id=?";

        String updateSQL =
                "UPDATE hotels SET password_hash=?,password_salt=? " +
                "WHERE hotel_id=?";

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                String storedHash;
                String storedSalt;

                try (PreparedStatement ps =
                             con.prepareStatement(selectSQL)) {

                    ps.setInt(1, HOTEL_ID);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new SQLException("Hotel account not found.");
                        }

                        storedHash = rs.getString("password_hash");
                        storedSalt = rs.getString("password_salt");
                    }
                }

                if (!verifyPassword(
                        oldPassword,
                        storedHash,
                        storedSalt)) {

                    throw new IllegalArgumentException(
                            "Current password is incorrect.");
                }

                byte[] newSalt = generateSalt();
                String newHash = hashPassword(newPassword, newSalt);

                String encodedSalt =
                        Base64.getEncoder().encodeToString(newSalt);

                try (PreparedStatement ps =
                             con.prepareStatement(updateSQL)) {

                    ps.setString(1, newHash);
                    ps.setString(2, encodedSalt);
                    ps.setInt(3, HOTEL_ID);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Password could not be updated.");
                    }
                }

                con.commit();
                sendSuccess(exchange, "Password changed successfully.");

            } catch (Exception e) {
                con.rollback();
                throw e;

            } finally {
                con.setAutoCommit(true);
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // HOTEL INFORMATION
    // ==================================================

    private static void hotelInfo(HttpExchange exchange)
            throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT hotel_name,username,email " +
                     "FROM hotels WHERE hotel_id=?")) {

            ps.setInt(1, HOTEL_ID);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    sendError(exchange, "Hotel information not found.");
                    return;
                }

                String json =
                        "{\"hotelName\":\"" + jsonEscape(rs.getString("hotel_name")) +
                        "\",\"username\":\"" + jsonEscape(rs.getString("username")) +
                        "\",\"email\":\"" + jsonEscape(rs.getString("email")) + "\"}";

                sendJson(exchange, json);
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // ADD FLOOR
    // ==================================================

    private static void addFloor(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "POST request required.");
            return;
        }

        Map<String, String> form = parseForm(readBody(exchange));

        int floorNumber = intValue(form.get("floorNumber"), -1);
        int roomsCount = intValue(form.get("roomsCount"), -1);

        String floorName = form.get("floorName");
        String roomType = form.get("roomType");

        if (floorNumber < 1 || floorNumber > 21474835 ||
                roomsCount < 1 || roomsCount > 99 ||
                floorName == null || floorName.trim().isEmpty() ||
                roomType == null || roomType.trim().isEmpty()) {

            sendError(exchange, "Please enter valid floor details.");
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try {
                String checkFloor =
                        "SELECT floor_number FROM hotel_floors " +
                        "WHERE hotel_id=? AND floor_number=?";

                try (PreparedStatement ps = connection.prepareStatement(checkFloor)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, floorNumber);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new SQLException("This floor already exists.");
                        }
                    }
                }

                String insertFloor =
                        "INSERT INTO hotel_floors " +
                        "(hotel_id,floor_number,floor_name) VALUES(?,?,?)";

                try (PreparedStatement ps = connection.prepareStatement(insertFloor)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, floorNumber);
                    ps.setString(3, floorName.trim());
                    ps.executeUpdate();
                }

                createRooms(connection, floorNumber, roomsCount, roomType);

                connection.commit();

                sendJson(exchange,
                        "{\"success\":true,\"roomsCreated\":" + roomsCount +
                        ",\"message\":\"Floor and rooms added successfully.\"}");

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // DELETE FLOOR
    // ==================================================

    private static void deleteFloor(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "POST request required.");
            return;
        }

        Map<String, String> form = parseForm(readBody(exchange));

        int floorNumber = intValue(form.get("floorNumber"), -1);

        if (floorNumber < 1) {
            sendError(exchange, "Invalid floor number.");
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try {
                String checkBookings =
                        "SELECT COUNT(*) FROM bookings b " +
                        "JOIN rooms r ON b.hotel_id=r.hotel_id " +
                        "AND b.room_number=r.room_number " +
                        "WHERE r.hotel_id=? AND r.floor_number=?";

                try (PreparedStatement ps = connection.prepareStatement(checkBookings)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, floorNumber);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new SQLException(
                                    "This floor contains booking history and cannot be deleted.");
                        }
                    }
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM rooms WHERE hotel_id=? AND floor_number=?")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, floorNumber);
                    ps.executeUpdate();
                }

                int deleted;

                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM hotel_floors WHERE hotel_id=? AND floor_number=?")) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, floorNumber);
                    deleted = ps.executeUpdate();
                }

                if (deleted == 0) {
                    throw new SQLException("Floor not found.");
                }

                connection.commit();
                sendSuccess(exchange, "Floor deleted successfully.");

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // ADD ROOMS TO A FLOOR
    // ==================================================

    private static void addRooms(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "POST request required.");
            return;
        }

        Map<String, String> form = parseForm(readBody(exchange));

        int floorNumber = intValue(form.get("floorNumber"), -1);
        int roomsCount = intValue(form.get("roomsCount"), -1);
        String roomType = form.get("roomType");

        if (floorNumber < 1 ||
                roomsCount < 1 || roomsCount > 99 ||
                roomType == null || roomType.trim().isEmpty()) {

            sendError(exchange, "Please enter valid room details.");
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try {
                String checkFloor =
                        "SELECT floor_number FROM hotel_floors " +
                        "WHERE hotel_id=? AND floor_number=?";

                try (PreparedStatement ps = connection.prepareStatement(checkFloor)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, floorNumber);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Selected floor does not exist.");
                        }
                    }
                }

                createRooms(connection, floorNumber, roomsCount, roomType);

                connection.commit();

                sendJson(exchange,
                        "{\"success\":true,\"roomsCreated\":" + roomsCount +
                        ",\"message\":\"Rooms added successfully.\"}");

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // ADD ROOMS TO EVERY FLOOR
    // ==================================================

    private static void addRoomsEveryFloor(HttpExchange exchange)
            throws IOException {

        if (!isPost(exchange)) {
            sendError(exchange, "POST request required.");
            return;
        }

        Map<String, String> form = parseForm(readBody(exchange));

        int roomsPerFloor = intValue(form.get("roomsPerFloor"), -1);
        String roomType = form.get("roomType");

        if (roomsPerFloor < 1 || roomsPerFloor > 99 ||
                roomType == null || roomType.trim().isEmpty()) {

            sendError(exchange, "Please enter valid room details.");
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try {
                List<Integer> floors = new ArrayList<>();

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT floor_number FROM hotel_floors " +
                        "WHERE hotel_id=? ORDER BY floor_number")) {

                    ps.setInt(1, HOTEL_ID);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            floors.add(rs.getInt("floor_number"));
                        }
                    }
                }

                if (floors.isEmpty()) {
                    throw new SQLException("No floors are available.");
                }

                int totalRoomsCreated = 0;

                for (int floorNumber : floors) {
                    createRooms(connection, floorNumber, roomsPerFloor, roomType);
                    totalRoomsCreated += roomsPerFloor;
                }

                connection.commit();

                sendJson(exchange,
                        "{\"success\":true,\"roomsCreated\":" + totalRoomsCreated +
                        ",\"message\":\"" + totalRoomsCreated +
                        " rooms added across " + floors.size() +
                        " floors successfully.\"}");

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }

        } catch (Exception e) {
            sendError(exchange, e.getMessage());
        }
    }

    // ==================================================
    // SHARED ROOM CREATION HELPER
    // ==================================================

    private static void createRooms(
            Connection connection,
            int floorNumber,
            int roomsCount,
            String roomType) throws SQLException {

        double price;

        String priceQuery =
                "SELECT price FROM room_types " +
                "WHERE hotel_id=? AND room_type=?";

        try (PreparedStatement ps = connection.prepareStatement(priceQuery)) {

            ps.setInt(1, HOTEL_ID);
            ps.setString(2, roomType.trim());

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    throw new SQLException("Selected room type does not exist.");
                }

                price = rs.getDouble("price");
            }
        }

        int lastSuffix = 0;

        String numberQuery =
                "SELECT COALESCE(MAX(MOD(room_number,100)),0) " +
                "FROM rooms WHERE hotel_id=? AND floor_number=?";

        try (PreparedStatement ps = connection.prepareStatement(numberQuery)) {

            ps.setInt(1, HOTEL_ID);
            ps.setInt(2, floorNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    lastSuffix = rs.getInt(1);
                }
            }
        }

        if (lastSuffix + roomsCount > 99) {
            throw new SQLException(
                    "A floor can have a maximum of 99 numbered rooms.");
        }

        String insertRoom =
                "INSERT INTO rooms " +
                "(room_number,floor_number,room_type,price,available,hotel_id) " +
                "VALUES(?,?,?,?,1,?)";

        try (PreparedStatement ps = connection.prepareStatement(insertRoom)) {

            for (int i = 1; i <= roomsCount; i++) {

                int roomNumber =
                        floorNumber * 100 + lastSuffix + i;

                ps.setInt(1, roomNumber);
                ps.setInt(2, floorNumber);
                ps.setString(3, roomType.trim());
                ps.setDouble(4, price);
                ps.setInt(5, HOTEL_ID);

                ps.addBatch();
            }

            ps.executeBatch();
        }
    }

    // ==================================================
    // MESSAGE PAGE
    // ==================================================

    private static String messagePage(
            String title,
            String message,
            String link,
            String linkText) {

        return "<!DOCTYPE html>" +
                "<html lang='en'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>" + esc(title) + " | Grand Stay</title>" +
                "<style>" +
                "body{margin:0;min-height:100vh;display:grid;place-items:center;" +
                "font-family:Arial,sans-serif;background:#18211f;color:#fff;}" +
                ".card{width:min(420px,85%);padding:35px;border-radius:16px;" +
                "background:#202a29;border:1px solid #34413e;text-align:center;}" +
                "h1{color:#83c99e;}p{color:#c2cbc7;line-height:1.6;}" +
                "a{display:inline-block;margin-top:18px;padding:12px 22px;" +
                "border-radius:8px;background:#83c99e;color:#18211f;" +
                "text-decoration:none;font-weight:bold;}" +
                "</style></head><body><div class='card'>" +
                "<h1>" + esc(title) + "</h1>" +
                "<p>" + esc(message) + "</p>" +
                "<a href='" + esc(link) + "'>" + esc(linkText) + "</a>" +
                "</div></body></html>";
    }

    // ==================================================
    // END OF HOTEL SERVER
    // ==================================================
}