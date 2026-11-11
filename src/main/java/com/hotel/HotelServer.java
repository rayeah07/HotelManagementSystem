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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HotelServer {

    private static final int HOTEL_ID = 1;

    private static final Path WEB =
            Paths.get("webapp")
                    .toAbsolutePath()
                    .normalize();

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

        // PAGES

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

        // LOGIN

        server.createContext("/login", HotelServer::login);

        // DASHBOARD AND ROOM DATA

        server.createContext("/dashboard-data",
                HotelServer::dashboardData);

        server.createContext("/all-rooms",
                HotelServer::allRooms);

        server.createContext("/available-rooms",
                HotelServer::availableRooms);

        // BOOKINGS

        server.createContext("/booking-history",
                HotelServer::bookingHistory);

        server.createContext("/book",
                HotelServer::book);

        server.createContext("/cancel-booking",
                HotelServer::cancelBooking);

        server.createContext("/checkout",
                HotelServer::checkout);

        // ROOM SERVICES

        server.createContext("/room-service",
                HotelServer::roomService);

        server.createContext("/service-list",
                HotelServer::serviceList);

        // INVOICES

        server.createContext("/invoice",
                HotelServer::invoice);

        server.createContext("/invoice-pdf",
                HotelServer::invoicePdf);

        // FLOOR AND ROOM MANAGEMENT

        server.createContext("/add-floor",
                HotelServer::addFloor);

        server.createContext("/delete-floor",
                HotelServer::deleteFloor);

        server.createContext("/add-rooms",
                HotelServer::addRooms);

        server.createContext("/add-rooms-every-floor",
                HotelServer::addRoomsEveryFloor);

        // SETTINGS AND PRICING

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

        Map<String, String> data =
                new LinkedHashMap<>();

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

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(bytes);
        }
    }

    private static void sendJson(
            HttpExchange exchange,
            String json) throws IOException {

        sendResponse(
                exchange,
                "application/json",
                json,
                200
        );
    }

    private static void sendHtml(
            HttpExchange exchange,
            String html) throws IOException {

        sendResponse(
                exchange,
                "text/html",
                html,
                200
        );
    }

    private static void sendError(
            HttpExchange exchange,
            String message) throws IOException {

        sendJson(
                exchange,
                "{\"success\":false,\"message\":\""
                        + jsonEscape(message)
                        + "\"}"
        );
    }

    private static void sendSuccess(
            HttpExchange exchange,
            String message) throws IOException {

        sendJson(
                exchange,
                "{\"success\":true,\"message\":\""
                        + jsonEscape(message)
                        + "\"}"
        );
    }

    private static void redirect(
            HttpExchange exchange,
            String location) throws IOException {

        exchange.getResponseHeaders().set(
                "Location",
                location
        );

        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    // ==================================================
    // FILE SERVING
    // ==================================================

    private static void serveFile(
            HttpExchange exchange,
            String fileName) throws IOException {

        Path file =
                WEB.resolve(fileName).normalize();

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

        exchange.sendResponseHeaders(
                200,
                bytes.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(bytes);
        }
    }

    // ==================================================
    // DATABASE HELPERS
    // ==================================================

    private static int scalarInt(
            Connection con,
            String query) throws SQLException {

        try (PreparedStatement ps =
                     con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }

            return 0;
        }
    }

    private static double scalarDouble(
            Connection con,
            String query) throws SQLException {

        try (PreparedStatement ps =
                     con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getDouble(1);
            }

            return 0.0;
        }
    }

    private static boolean isPost(HttpExchange exchange) {

        return "POST".equalsIgnoreCase(
                exchange.getRequestMethod()
        );
    }

    private static boolean isGet(HttpExchange exchange) {

        return "GET".equalsIgnoreCase(
                exchange.getRequestMethod()
        );
    }

    private static String getQueryValue(
            HttpExchange exchange,
            String key) {

        Map<String, String> query =
                parseForm(
                        exchange.getRequestURI().getRawQuery()
                );

        return query.getOrDefault(key, "");
    }

    // ==================================================
    // LOGIN
    // ==================================================

    private static void login(
            HttpExchange exchange) throws IOException {

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

        try (Connection con =
                     DatabaseConnection.getConnection();
             PreparedStatement ps =
                     con.prepareStatement(
                             "SELECT hotel_id FROM hotels "
                                     + "WHERE username=? "
                                     + "AND password_hash=?"
                     )) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next() && rs.getInt("hotel_id") == HOTEL_ID) {
                    redirect(exchange, "/dashboard.html");
                } else {
                    sendHtml(
                            exchange,
                            messagePage(
                                    "Login Failed",
                                    "Invalid username or password.",
                                    "/",
                                    "Try Again"
                            )
                    );
                }
            }

        } catch (Exception e) {

            sendHtml(
                    exchange,
                    messagePage(
                            "Login Error",
                            e.getMessage(),
                            "/",
                            "Back"
                    )
            );
        }
    }

    // ==================================================
    // DASHBOARD DATA
    // ==================================================

    private static void dashboardData(
            HttpExchange exchange) throws IOException {

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

                json.append("{");
                json.append("\"totalRooms\":").append(totalRooms).append(",");
                json.append("\"availableRooms\":").append(availableRooms).append(",");
                json.append("\"bookedRooms\":").append(totalRooms - availableRooms).append(",");
                json.append("\"totalBookings\":").append(totalBookings).append(",");
                json.append("\"revenue\":").append(revenue).append(",");
                json.append("\"rooms\":[");

                String roomSql =
                        "SELECT room_number,floor_number,room_type,price,available " +
                        "FROM rooms WHERE hotel_id=? ORDER BY room_number";

                try (PreparedStatement roomPs = con.prepareStatement(roomSql)) {

                    roomPs.setInt(1, HOTEL_ID);

                    try (ResultSet rooms = roomPs.executeQuery()) {

                        boolean first = true;

                        while (rooms.next()) {

                            if (!first) {
                                json.append(",");
                            }

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

                            if (!first) {
                                json.append(",");
                            }

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
    // ALL ROOMS
    // ==================================================

    private static void allRooms(HttpExchange exchange)
            throws IOException {

        roomList(exchange, false);
    }

    // ==================================================
    // AVAILABLE ROOMS
    // ==================================================

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

                    if (!first) {
                        json.append(",");
                    }

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

                    if (!first) {
                        json.append(",");
                    }

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
                data.getOrDefault(
                        "roomNumber",
                        data.getOrDefault("room", "0")
                ),
                0
        );

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
                        customerSql,
                        Statement.RETURN_GENERATED_KEYS)) {

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
                        bookingSql,
                        Statement.RETURN_GENERATED_KEYS)) {

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

                redirect(
                        exchange,
                        "/bookings.html?success=" +
                                urlEncode("Booking created successfully. Booking ID: " + bookingId)
                );

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
        int bookingId = intValue(data.get("bookingId"), 0);

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
                                    "Only active bookings can be cancelled."
                            );
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

                redirect(
                        exchange,
                        "/bookings.html?success=" +
                                urlEncode("Booking cancelled successfully.")
                );

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
        int bookingId = intValue(data.get("bookingId"), 0);

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
                                    "Only active bookings can be checked out."
                            );
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

                redirect(exchange, "/invoice?booking_id=" + bookingId);

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

        int bookingId = intValue(data.get("bookingId"), 0);
        int quantity = intValue(data.get("quantity"), 1);

        String serviceName =
                data.getOrDefault("serviceName", "").trim();

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
                            "Services can only be added to active bookings."
                    );
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
                                    "This service is not available."
                            );
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

                redirect(
                        exchange,
                        "/bookings.html?success=" +
                                urlEncode("Service added successfully.")
                );

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
                getQueryValue(exchange, "booking_id"),
                0
        );

        try (Connection con = DatabaseConnection.getConnection()) {

            // When a booking ID is supplied, return its service history.
            if (bookingId > 0) {

                String sql =
                        "SELECT service_name,quantity,price,total,service_date " +
                        "FROM room_services " +
                        "WHERE hotel_id=? AND booking_id=? " +
                        "ORDER BY service_id DESC";

                try (PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setInt(1, HOTEL_ID);
                    ps.setInt(2, bookingId);

                    try (ResultSet rs = ps.executeQuery()) {

                        StringBuilder json = new StringBuilder("[");
                        boolean first = true;

                        while (rs.next()) {

                            if (!first) {
                                json.append(",");
                            }

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

                // Without a booking ID, return the service catalogue.
                String sql =
                        "SELECT service_name,price FROM service_prices " +
                        "WHERE hotel_id=? ORDER BY service_name";

                try (PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setInt(1, HOTEL_ID);

                    try (ResultSet rs = ps.executeQuery()) {

                        StringBuilder json = new StringBuilder("[");
                        boolean first = true;

                        while (rs.next()) {

                            if (!first) {
                                json.append(",");
                            }

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
                getQueryValue(exchange, "booking_id"),
                0
        );

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
                    "FROM room_services " +
                    "WHERE hotel_id=? AND booking_id=? ORDER BY service_id";

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
                    "<!DOCTYPE html>" +
                    "<html><head>" +
                    "<meta charset='UTF-8'>" +
                    "<title>Invoice #" + bookingId + "</title>" +
                    "<link rel='stylesheet' href='/css/style.css'>" +
                    "</head><body class='invoice-page'>" +

                    "<div class='invoice-card'>" +

                    "<div class='invoice-head'>" +
                    "<div><div class='invoice-logo'>GS</div>" +
                    "<h1>Grand Stay Hotel</h1>" +
                    "<p>Guest Invoice</p></div>" +
                    "<div class='invoice-id'>Invoice #" + bookingId + "</div>" +
                    "</div>" +

                    "<div class='invoice-info'>" +
                    "<div><b>Guest</b><br>" +
                    esc(name) + "<br>" + esc(phone) + "</div>" +

                    "<div><b>Room</b><br>" +
                    room + " - " + esc(roomType) +
                    "<br>" + days + " night(s)</div>" +

                    "<div><b>Status</b><br>" +
                    esc(status) + "<br>" +
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
    // SAVE INVOICE AS PDF
    // ==================================================

    private static void invoicePdf(HttpExchange exchange)
            throws IOException {

        if (!isGet(exchange)) {
            sendError(exchange, "Invalid request.");
            return;
        }

        int bookingId = intValue(
                getQueryValue(exchange, "booking_id"),
                0
        );

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
            lines.add("Room total: INR " + String.format(Locale.US, "%.2f", roomTotal));
            lines.add("Services: INR " + String.format(Locale.US, "%.2f", serviceTotal));
            lines.add("Grand total: INR " + String.format(Locale.US, "%.2f", grandTotal));

            byte[] pdf = simplePdf(lines);

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/pdf"
            );

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

    // ==================================================
    // SIMPLE PDF GENERATOR
    // ==================================================

    private static byte[] simplePdf(List<String> lines)
            throws IOException {

        StringBuilder stream = new StringBuilder();

        stream.append("BT /F1 14 Tf 50 790 Td ");

        for (int i = 0; i < lines.size(); i++) {

            if (i > 0) {
                stream.append("0 -28 Td ");
            }

            String safe = lines.get(i)
                    .replace("\\", "\\\\")
                    .replace("(", "\\(")
                    .replace(")", "\\)");

            stream.append("(")
                    .append(safe)
                    .append(") Tj ");
        }

        stream.append("ET");

        String[] objects = {

                "<< /Type /Catalog /Pages 2 0 R >>",

                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",

                "<< /Type /Page /Parent 2 0 R " +
                "/MediaBox [0 0 595 842] " +
                "/Resources << /Font << /F1 5 0 R >> >> " +
                "/Contents 4 0 R >>",

                "<< /Length " + stream.length() + " >>\n" +
                "stream\n" + stream + "\nendstream",

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

        try (Connection con = DatabaseConnection.getConnection()) {

            StringBuilder json = new StringBuilder("{");

            // Room types
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

            // Floors
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

            // Service catalogue
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
            sendJson(exchange,
                    "{\"success\":false,\"message\":\"" +
                    jsonEscape(e.getMessage()) + "\"}");
        }
    }

    // ==================================================
    // UPDATE ROOM PRICES
    // ==================================================

    private static void updateRoomPrices(HttpExchange exchange)
            throws IOException {

        Map<String, String> data = parseForm(readBody(exchange));

        String roomType = data.getOrDefault("roomType", "").trim();
        double price = doubleValue(data.get("price"), -1);

        if (roomType.isBlank() || price < 0) {
            sendJson(exchange, "{\"success\":false,\"message\":\"Enter a valid room type and price.\"}");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

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
            sendJson(exchange, "{\"success\":true,\"message\":\"Room price updated.\"}");

        } catch (Exception e) {
            sendJson(exchange, "{\"success\":false,\"message\":\"" +
                    jsonEscape(e.getMessage()) + "\"}");
        }
    }

    // ==================================================
    // RENAME ROOM TYPE
    // ==================================================

    private static void renameRoomType(HttpExchange exchange)
            throws IOException {

        Map<String, String> data = parseForm(readBody(exchange));

        String oldName = data.getOrDefault("oldRoomType", "").trim();
        String newName = data.getOrDefault("newRoomType", "").trim();

        if (oldName.isBlank() || newName.isBlank()) {
            sendJson(exchange, "{\"success\":false,\"message\":\"Enter both room type names.\"}");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

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
            sendJson(exchange, "{\"success\":true,\"message\":\"Room type renamed.\"}");

        } catch (Exception e) {
            sendJson(exchange, "{\"success\":false,\"message\":\"" +
                    jsonEscape(e.getMessage()) + "\"}");
        }
    }

    // ==================================================
    // ADD SERVICE
    // ==================================================

    private static void addService(HttpExchange exchange)
            throws IOException {

        Map<String, String> data = parseForm(readBody(exchange));

        String name = data.getOrDefault("serviceName", "").trim();
        double price = doubleValue(data.get("price"), -1);

        if (name.isBlank() || price < 0) {
            sendJson(exchange, "{\"success\":false,\"message\":\"Enter a valid service name and price.\"}");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO service_prices (hotel_id,service_name,price) VALUES(?,?,?)")) {

            ps.setInt(1, HOTEL_ID);
            ps.setString(2, name);
            ps.setDouble(3, price);
            ps.executeUpdate();

            sendJson(exchange, "{\"success\":true,\"message\":\"Service added.\"}");

        } catch (Exception e) {
            sendJson(exchange, "{\"success\":false,\"message\":\"" +
                    jsonEscape(e.getMessage()) + "\"}");
        }
    }

    // ==================================================
    // UPDATE SERVICE PRICE
    // ==================================================

    private static void updateServicePrice(HttpExchange exchange)
            throws IOException {

        Map<String, String> data = parseForm(readBody(exchange));

        String name = data.getOrDefault("serviceName", "").trim();
        double price = doubleValue(data.get("price"), -1);

        if (name.isBlank() || price < 0) {
            sendJson(exchange, "{\"success\":false,\"message\":\"Enter a valid service and price.\"}");
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
                sendJson(exchange, "{\"success\":false,\"message\":\"Service not found.\"}");
                return;
            }

            sendJson(exchange, "{\"success\":true,\"message\":\"Service price updated.\"}");

        } catch (Exception e) {
            sendJson(exchange, "{\"success\":false,\"message\":\"" +
                    jsonEscape(e.getMessage()) + "\"}");
        }
    }

    // ==================================================
    // RENAME SERVICE
    // ==================================================

    private static void renameService(HttpExchange exchange)
            throws IOException {

        Map<String, String> data = parseForm(readBody(exchange));

        String oldName = data.getOrDefault("oldServiceName", "").trim();
        String newName = data.getOrDefault("newServiceName", "").trim();

        if (oldName.isBlank() || newName.isBlank()) {
            sendJson(exchange, "{\"success\":false,\"message\":\"Enter both service names.\"}");
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
                sendJson(exchange, "{\"success\":false,\"message\":\"Service not found.\"}");
                return;
            }

            sendJson(exchange, "{\"success\":true,\"message\":\"Service renamed.\"}");

        } catch (Exception e) {
            sendJson(exchange, "{\"success\":false,\"message\":\"" +
                    jsonEscape(e.getMessage()) + "\"}");
        }
    }

    // ==================================================
    // DELETE SERVICE
    // ==================================================

    private static void deleteService(HttpExchange exchange)
            throws IOException {

        Map<String, String> data = parseForm(readBody(exchange));

        String name = data.getOrDefault("serviceName", "").trim();

        if (name.isBlank()) {
            sendJson(exchange, "{\"success\":false,\"message\":\"Select a service.\"}");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM service_prices " +
                     "WHERE hotel_id=? AND service_name=?")) {

            ps.setInt(1, HOTEL_ID);
            ps.setString(2, name);

            if (ps.executeUpdate() == 0) {
                sendJson(exchange, "{\"success\":false,\"message\":\"Service not found.\"}");
                return;
            }

            sendJson(exchange, "{\"success\":true,\"message\":\"Service deleted.\"}");

        } catch (Exception e) {
            sendJson(exchange, "{\"success\":false,\"message\":\"" +
                    jsonEscape(e.getMessage()) + "\"}");
        }
    }

    // ==================================================
    // CHANGE PASSWORD
    // ==================================================

    private static void changePassword(HttpExchange exchange)
            throws IOException {

        Map<String, String> data = parseForm(readBody(exchange));

        String oldPassword = data.getOrDefault("oldPassword", "");
        String newPassword = data.getOrDefault("newPassword", "");
        String confirmPassword = data.getOrDefault("confirmPassword", "");

        if (!oldPassword.equals("admin123")) {
            sendJson(exchange, "{\"success\":false,\"message\":\"Current password is incorrect.\"}");
            return;
        }

        if (newPassword.length() < 6) {
            sendJson(exchange, "{\"success\":false,\"message\":\"New password must contain at least 6 characters.\"}");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            sendJson(exchange, "{\"success\":false,\"message\":\"New passwords do not match.\"}");
            return;
        }

        // The existing login in this project uses admin/admin123.
        // A database-backed password system must be implemented
        // consistently in both login and password-change handlers.
        sendJson(exchange,
                "{\"success\":false,\"message\":\"Password change is not enabled for the current login setup.\"}");
    }

    // ==================================================
    // HOTEL INFORMATION
    // ==================================================

    private static void hotelInfo(HttpExchange exchange)
            throws IOException {

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT hotel_name,username,email FROM hotels WHERE hotel_id=?")) {

            ps.setInt(1, HOTEL_ID);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    sendJson(exchange, "{\"success\":false,\"message\":\"Hotel information not found.\"}");
                    return;
                }

                String json =
                        "{\"hotelName\":\"" + jsonEscape(rs.getString("hotel_name")) +
                        "\",\"username\":\"" + jsonEscape(rs.getString("username")) +
                        "\",\"email\":\"" + jsonEscape(rs.getString("email")) + "\"}";

                sendJson(exchange, json);
            }

        } catch (Exception e) {
            sendJson(exchange, "{\"success\":false,\"message\":\"" +
                    jsonEscape(e.getMessage()) + "\"}");
        }
    }
    

    // ==================================================
    // END OF HOTEL SERVER
    // ==================================================

}