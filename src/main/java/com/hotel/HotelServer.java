package com.hotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class HotelServer {

    public static void main(String[] args) throws Exception {

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(8080), 0
                );

        server.createContext("/", HotelServer::home);
        server.createContext("/login", HotelServer::login);

        server.createContext("/dashboard.html",
                HotelServer::dashboard);

        server.createContext("/rooms.html",
                HotelServer::rooms);

        server.createContext("/booking.html",
                HotelServer::booking);

        server.createContext("/bookings.html",
                HotelServer::bookings);

        server.createContext("/css/style.css",
                HotelServer::css);

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

        // NEW BOOKING MANAGEMENT FEATURES
        server.createContext("/cancel-booking", HotelServer::cancelBooking);
        server.createContext("/checkout", HotelServer::checkout);
        server.createContext("/room-service", HotelServer::roomService);
        server.createContext("/service-list", HotelServer::serviceList);
        server.createContext("/invoice", HotelServer::invoice);
        server.createContext("/invoice-pdf", HotelServer::invoicePdf);

        server.start();

        System.out.println();
        System.out.println("==========================================");
        System.out.println("          GRAND STAY HOTEL");
        System.out.println("==========================================");
        System.out.println("Server running at:");
        System.out.println("http://localhost:8080");
        System.out.println();
        System.out.println("24 Rooms | 8 Floors");
        System.out.println("2 Single + 1 Deluxe per floor");
        System.out.println();
        System.out.println("Press Ctrl+C to stop the server.");
        System.out.println("==========================================");
    }


    // ==========================================
    // HOME
    // ==========================================

    private static void home(HttpExchange exchange)
            throws IOException {

        serveFile(exchange, "index.html");
    }


    // ==========================================
    // LOGIN
    // ==========================================

    private static void login(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {

            redirect(exchange, "/");
            return;
        }

        String body = readBody(exchange);

        Map<String, String> data =
                parseForm(body);

        String username =
                data.getOrDefault("username", "");

        String password =
                data.getOrDefault("password", "");

        if (username.equals("admin")
                && password.equals("admin123")) {

            redirect(exchange, "/dashboard.html");

        } else {

            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Login Failed</title>
                        <link rel="stylesheet"
                              href="/css/style.css">
                    </head>

                    <body>

                    <div class="message-page">

                        <div class="message-card">

                            <div class="message-icon">
                                !
                            </div>

                            <h1>Login Failed</h1>

                            <p>
                                Invalid username or password.
                            </p>

                            <a href="/">
                                Try Again
                            </a>

                        </div>

                    </div>

                    </body>
                    </html>
                    """;

            sendHtml(exchange, html);
        }
    }


    // ==========================================
    // DASHBOARD PAGE
    // ==========================================

    private static void dashboard(HttpExchange exchange)
            throws IOException {

        serveFile(exchange, "dashboard.html");
    }


    // ==========================================
    // ROOMS PAGE
    // ==========================================

    private static void rooms(HttpExchange exchange)
            throws IOException {

        serveFile(exchange, "rooms.html");
    }


    // ==========================================
    // BOOKING PAGE
    // ==========================================

    private static void booking(HttpExchange exchange)
            throws IOException {

        serveFile(exchange, "booking.html");
    }


    // ==========================================
    // BOOKING HISTORY PAGE
    // ==========================================

    private static void bookings(HttpExchange exchange)
            throws IOException {

        serveFile(exchange, "bookings.html");
    }


    // ==========================================
    // CSS
    // ==========================================

    private static void css(HttpExchange exchange)
            throws IOException {

        serveFile(exchange, "css/style.css");
    }


    // ==========================================
    // DASHBOARD DATA
    // ==========================================

    private static void dashboardData(
            HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            sendJson(exchange,
                    "{\"error\":\"Invalid request\"}");

            return;
        }

        try (Connection con =
                     DatabaseConnection.getConnection()) {

            int totalRooms = 0;
            int availableRooms = 0;
            int bookedRooms = 0;
            int totalBookings = 0;

            double revenue = 0;


            // ROOM STATISTICS
            String roomQuery = """
                    SELECT
                        COUNT(*) AS total,
                        SUM(
                            CASE
                                WHEN available = TRUE
                                THEN 1 ELSE 0
                            END
                        ) AS available,
                        SUM(
                            CASE
                                WHEN available = FALSE
                                THEN 1 ELSE 0
                            END
                        ) AS booked
                    FROM rooms
                    """;

            try (PreparedStatement ps =
                         con.prepareStatement(roomQuery);

                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    totalRooms = rs.getInt("total");
                    availableRooms =
                            rs.getInt("available");
                    bookedRooms =
                            rs.getInt("booked");
                }
            }


            // BOOKING STATISTICS
            String bookingQuery = """
                    SELECT
                        COUNT(*) AS total,
                        COALESCE(SUM(total), 0) AS revenue
                    FROM bookings
                    """;

            try (PreparedStatement ps =
                         con.prepareStatement(bookingQuery);

                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    totalBookings =
                            rs.getInt("total");

                    revenue =
                            rs.getDouble("revenue");
                }
            }


            StringBuilder json =
                    new StringBuilder();

            json.append("{");

            json.append("\"totalRooms\":")
                    .append(totalRooms)
                    .append(",");

            json.append("\"availableRooms\":")
                    .append(availableRooms)
                    .append(",");

            json.append("\"bookedRooms\":")
                    .append(bookedRooms)
                    .append(",");

            json.append("\"totalBookings\":")
                    .append(totalBookings)
                    .append(",");

            json.append("\"revenue\":")
                    .append(revenue)
                    .append(",");


            // ALL ROOMS
            json.append("\"rooms\":[");

            String roomsQuery = """
                    SELECT
                        room_number,
                        floor_number,
                        room_type,
                        price,
                        available
                    FROM rooms
                    ORDER BY room_number
                    """;

            try (PreparedStatement ps =
                         con.prepareStatement(roomsQuery);

                 ResultSet rs = ps.executeQuery()) {

                boolean first = true;

                while (rs.next()) {

                    if (!first)
                        json.append(",");

                    first = false;

                    json.append("{");

                    json.append("\"roomNumber\":")
                            .append(rs.getInt("room_number"))
                            .append(",");

                    json.append("\"floor\":")
                            .append(rs.getInt("floor_number"))
                            .append(",");

                    json.append("\"type\":\"")
                            .append(jsonEscape(
                                    rs.getString("room_type")))
                            .append("\",");

                    json.append("\"price\":")
                            .append(rs.getDouble("price"))
                            .append(",");

                    json.append("\"available\":")
                            .append(rs.getBoolean("available"));

                    json.append("}");
                }
            }

            json.append("],");


            // RECENT BOOKINGS
            json.append("\"recentBookings\":[");

            String recentQuery = """
                    SELECT
                        b.booking_id,
                        c.name,
                        b.room_number,
                        r.room_type,
                        b.days,
                        b.total,
                        b.status,
                        b.booking_date,
                        b.checkout_date,
                        COALESCE((
                            SELECT SUM(rs.total)
                            FROM room_services rs
                            WHERE rs.booking_id = b.booking_id
                        ), 0) AS service_total
                    FROM bookings b
                    JOIN customers c
                        ON b.customer_id = c.customer_id
                    JOIN rooms r
                        ON b.room_number = r.room_number
                    ORDER BY b.booking_id DESC
                    LIMIT 5
                    """;

            try (PreparedStatement ps =
                         con.prepareStatement(recentQuery);

                 ResultSet rs = ps.executeQuery()) {

                boolean first = true;

                while (rs.next()) {

                    if (!first)
                        json.append(",");

                    first = false;

                    json.append("{");

                    json.append("\"bookingId\":")
                            .append(rs.getInt("booking_id"))
                            .append(",");

                    json.append("\"name\":\"")
                            .append(jsonEscape(
                                    rs.getString("name")))
                            .append("\",");

                    json.append("\"room\":")
                            .append(rs.getInt("room_number"))
                            .append(",");

                    json.append("\"type\":\"")
                            .append(jsonEscape(
                                    rs.getString("room_type")))
                            .append("\",");

                    json.append("\"days\":")
                            .append(rs.getInt("days"))
                            .append(",");

                    json.append("\"total\":")
                            .append(rs.getDouble("total"))
                            .append(",");

                    json.append("\"date\":\"")
                            .append(jsonEscape(
                                    rs.getString(
                                            "booking_date")))
                            .append("\"");

                    json.append("}");
                }
            }

            json.append("]");

            json.append("}");

            sendJson(exchange, json.toString());

        } catch (Exception e) {

            e.printStackTrace();

            sendJson(
                    exchange,
                    "{\"error\":\"Database error\"}"
            );
        }
    }


    // ==========================================
    // ALL ROOMS
    // ==========================================

    private static void allRooms(HttpExchange exchange)
            throws IOException {

        try (Connection con =
                     DatabaseConnection.getConnection()) {

            String query = """
                    SELECT
                        room_number,
                        floor_number,
                        room_type,
                        price,
                        available
                    FROM rooms
                    ORDER BY room_number
                    """;

            StringBuilder json =
                    new StringBuilder("[");

            try (PreparedStatement ps =
                         con.prepareStatement(query);

                 ResultSet rs =
                         ps.executeQuery()) {

                boolean first = true;

                while (rs.next()) {

                    if (!first)
                        json.append(",");

                    first = false;

                    json.append("{");

                    json.append("\"roomNumber\":")
                            .append(rs.getInt("room_number"))
                            .append(",");

                    json.append("\"floor\":")
                            .append(rs.getInt("floor_number"))
                            .append(",");

                    json.append("\"type\":\"")
                            .append(jsonEscape(
                                    rs.getString("room_type")))
                            .append("\",");

                    json.append("\"price\":")
                            .append(rs.getDouble("price"))
                            .append(",");

                    json.append("\"available\":")
                            .append(rs.getBoolean("available"));

                    json.append("}");
                }
            }

            json.append("]");

            sendJson(exchange, json.toString());

        } catch (Exception e) {

            e.printStackTrace();

            sendJson(
                    exchange,
                    "{\"error\":\"Database error\"}"
            );
        }
    }


    // ==========================================
    // AVAILABLE ROOMS
    // ==========================================

    private static void availableRooms(
            HttpExchange exchange)
            throws IOException {

        try (Connection con =
                     DatabaseConnection.getConnection()) {

            String query = """
                    SELECT
                        room_number,
                        floor_number,
                        room_type,
                        price
                    FROM rooms
                    WHERE available = TRUE
                    ORDER BY room_number
                    """;

            StringBuilder json =
                    new StringBuilder("[");

            try (PreparedStatement ps =
                         con.prepareStatement(query);

                 ResultSet rs =
                         ps.executeQuery()) {

                boolean first = true;

                while (rs.next()) {

                    if (!first)
                        json.append(",");

                    first = false;

                    json.append("{");

                    json.append("\"roomNumber\":")
                            .append(rs.getInt("room_number"))
                            .append(",");

                    json.append("\"floor\":")
                            .append(rs.getInt("floor_number"))
                            .append(",");

                    json.append("\"type\":\"")
                            .append(jsonEscape(
                                    rs.getString("room_type")))
                            .append("\",");

                    json.append("\"price\":")
                            .append(rs.getDouble("price"));

                    json.append("}");
                }
            }

            json.append("]");

            sendJson(exchange, json.toString());

        } catch (Exception e) {

            e.printStackTrace();

            sendJson(
                    exchange,
                    "{\"error\":\"Database error\"}"
            );
        }
    }


    // ==========================================
    // BOOKING HISTORY
    // ==========================================

    private static void bookingHistory(
            HttpExchange exchange)
            throws IOException {

        try (Connection con =
                     DatabaseConnection.getConnection()) {

            String query = """
                    SELECT
                        b.booking_id,
                        c.name,
                        c.phone,
                        b.room_number,
                        r.room_type,
                        b.days,
                        b.total,
                        b.status,
                        b.booking_date,
                        b.checkout_date,
                        COALESCE((
                            SELECT SUM(rs.total)
                            FROM room_services rs
                            WHERE rs.booking_id = b.booking_id
                        ), 0) AS service_total
                    FROM bookings b
                    JOIN customers c
                        ON b.customer_id = c.customer_id
                    JOIN rooms r
                        ON b.room_number = r.room_number
                    ORDER BY b.booking_id DESC
                    """;

            StringBuilder json =
                    new StringBuilder("[");

            try (PreparedStatement ps =
                         con.prepareStatement(query);

                 ResultSet rs =
                         ps.executeQuery()) {

                boolean first = true;

                while (rs.next()) {

                    if (!first)
                        json.append(",");

                    first = false;

                    json.append("{");

                    json.append("\"id\":")
                            .append(rs.getInt("booking_id"))
                            .append(",");

                    json.append("\"name\":\"")
                            .append(jsonEscape(
                                    rs.getString("name")))
                            .append("\",");

                    json.append("\"phone\":\"")
                            .append(jsonEscape(
                                    rs.getString("phone")))
                            .append("\",");

                    json.append("\"room\":")
                            .append(rs.getInt("room_number"))
                            .append(",");

                    json.append("\"type\":\"")
                            .append(jsonEscape(
                                    rs.getString("room_type")))
                            .append("\",");

                    json.append("\"days\":")
                            .append(rs.getInt("days"))
                            .append(",");

                    json.append("\"total\":")
                            .append(rs.getDouble("total"))
                            .append(",");

                    json.append("\"serviceTotal\":")
                            .append(rs.getDouble("service_total"))
                            .append(",");

                    json.append("\"status\":\"")
                            .append(jsonEscape(rs.getString("status")))
                            .append("\",");

                    json.append("\"date\":\"")
                            .append(jsonEscape(
                                    rs.getString(
                                            "booking_date")))
                            .append("\",");

                    String checkoutDate = rs.getString("checkout_date");
                    json.append("\"checkoutDate\":");
                    if (checkoutDate == null) {
                        json.append("null");
                    } else {
                        json.append("\"")
                                .append(jsonEscape(checkoutDate))
                                .append("\"");
                    }

                    json.append("}");
                }
            }

            json.append("]");

            sendJson(exchange, json.toString());

        } catch (Exception e) {

            e.printStackTrace();

            sendJson(
                    exchange,
                    "{\"error\":\"Database error\"}"
            );
        }
    }


    // ==========================================
    // CREATE BOOKING
    // ==========================================

    private static void book(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("POST")) {

            redirect(exchange, "/booking.html");
            return;
        }

        String body =
                readBody(exchange);

        Map<String, String> data =
                parseForm(body);

        String name =
                data.getOrDefault("name", "").trim();

        String phone =
                data.getOrDefault("phone", "").trim();

        int roomNumber;

        int days;

        try {

            roomNumber =
                    Integer.parseInt(
                            data.getOrDefault(
                                    "room",
                                    "0"));

            days =
                    Integer.parseInt(
                            data.getOrDefault(
                                    "days",
                                    "0"));

        } catch (NumberFormatException e) {

            sendErrorPage(
                    exchange,
                    "Invalid room or number of days."
            );

            return;
        }

        if (name.isEmpty()
                || phone.isEmpty()
                || days <= 0) {

            sendErrorPage(
                    exchange,
                    "Please fill all fields correctly."
            );

            return;
        }


        Connection con = null;

        try {

            con =
                    DatabaseConnection.getConnection();

            con.setAutoCommit(false);


            // CHECK ROOM
            String roomQuery = """
                    SELECT
                        room_type,
                        price,
                        available
                    FROM rooms
                    WHERE room_number = ?
                    FOR UPDATE
                    """;

            String roomType;

            double price;

            boolean available;

            try (PreparedStatement ps =
                         con.prepareStatement(roomQuery)) {

                ps.setInt(1, roomNumber);

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {

                        con.rollback();

                        sendErrorPage(
                                exchange,
                                "Room does not exist."
                        );

                        return;
                    }

                    roomType =
                            rs.getString("room_type");

                    price =
                            rs.getDouble("price");

                    available =
                            rs.getBoolean("available");
                }
            }


            if (!available) {

                con.rollback();

                sendErrorPage(
                        exchange,
                        "This room is already booked."
                );

                return;
            }


            double total =
                    price * days;


            // INSERT CUSTOMER
            int customerId;

            String customerQuery = """
                    INSERT INTO customers
                    (name, phone)
                    VALUES (?, ?)
                    """;

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 customerQuery,
                                 Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, name);
                ps.setString(2, phone);

                ps.executeUpdate();

                try (ResultSet keys =
                             ps.getGeneratedKeys()) {

                    if (!keys.next()) {

                        con.rollback();

                        sendErrorPage(
                                exchange,
                                "Could not create customer."
                        );

                        return;
                    }

                    customerId =
                            keys.getInt(1);
                }
            }


            // INSERT BOOKING
            int bookingId;

            String bookingQuery = """
                    INSERT INTO bookings
                    (customer_id, room_number, days, total, status)
                    VALUES (?, ?, ?, ?, 'ACTIVE')
                    """;

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 bookingQuery,
                                 Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, customerId);
                ps.setInt(2, roomNumber);
                ps.setInt(3, days);
                ps.setDouble(4, total);

                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        con.rollback();
                        sendErrorPage(
                                exchange,
                                "Could not create booking.");
                        return;
                    }

                    bookingId = keys.getInt(1);
                }
            }


            // UPDATE ROOM
            String updateRoom = """
                    UPDATE rooms
                    SET available = FALSE
                    WHERE room_number = ?
                    """;

            try (PreparedStatement ps =
                         con.prepareStatement(updateRoom)) {

                ps.setInt(1, roomNumber);

                ps.executeUpdate();
            }


            con.commit();


            // SUCCESS PAGE

            String html = """
                    <!DOCTYPE html>
                    <html>

                    <head>
                        <title>Booking Confirmed</title>

                        <link rel="stylesheet"
                              href="/css/style.css">
                    </head>

                    <body>

                    <div class="message-page">

                        <div class="success-card">

                            <div class="success-icon">
                                ✓
                            </div>

                            <h1>Booking Confirmed</h1>

                            <p class="success-text">
                                Your reservation has been
                                successfully created.
                            </p>

                            <div class="confirmation-box">

                                <div>
                                    <span>Booking ID</span>
                                    <strong>
                                        #%d
                                    </strong>
                                </div>

                                <div>
                                    <span>Guest</span>
                                    <strong>
                                        %s
                                    </strong>
                                </div>

                                <div>
                                    <span>Room</span>
                                    <strong>
                                        %d
                                    </strong>
                                </div>

                                <div>
                                    <span>Room Type</span>
                                    <strong>
                                        %s
                                    </strong>
                                </div>

                                <div>
                                    <span>Days</span>
                                    <strong>
                                        %d
                                    </strong>
                                </div>

                                <div>
                                    <span>Total</span>
                                    <strong>
                                        ₹%.2f
                                    </strong>
                                </div>

                            </div>

                            <div class="message-buttons">

                                <a href="/dashboard.html">
                                    Dashboard
                                </a>

                                <a href="/booking.html">
                                    New Booking
                                </a>

                            </div>

                        </div>

                    </div>

                    </body>
                    </html>
                    """.formatted(
                            bookingId,
                            escapeHtml(name),
                            roomNumber,
                            escapeHtml(roomType),
                            days,
                            total
                    );

            sendHtml(exchange, html);

        } catch (Exception e) {

            if (con != null) {

                try {
                    con.rollback();
                } catch (Exception ignored) {
                }
            }

            e.printStackTrace();

            sendErrorPage(
                    exchange,
                    "Booking failed. Please try again."
            );

        } finally {

            if (con != null) {

                try {
                    con.close();
                } catch (Exception ignored) {
                }
            }
        }
    }



    // ==========================================
    // CANCEL BOOKING
    // ==========================================

    private static void cancelBooking(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendErrorPage(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        int bookingId;
        try {
            bookingId = Integer.parseInt(
                    data.getOrDefault("booking_id", "0"));
        } catch (NumberFormatException e) {
            sendErrorPage(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            int roomNumber;

            String find = """
                    SELECT room_number, status
                    FROM bookings
                    WHERE booking_id = ?
                    FOR UPDATE
                    """;

            try (PreparedStatement ps = con.prepareStatement(find)) {
                ps.setInt(1, bookingId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        sendErrorPage(exchange, "Booking not found.");
                        return;
                    }

                    if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                        con.rollback();
                        sendErrorPage(exchange,
                                "Only active bookings can be cancelled.");
                        return;
                    }

                    roomNumber = rs.getInt("room_number");
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE bookings SET status='CANCELLED' WHERE booking_id=?")) {
                ps.setInt(1, bookingId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE rooms SET available=TRUE WHERE room_number=?")) {
                ps.setInt(1, roomNumber);
                ps.executeUpdate();
            }

            con.commit();

            sendActionPage(exchange,
                    "Booking Cancelled",
                    "Booking #" + bookingId + " has been cancelled successfully.",
                    "/bookings.html",
                    "Back to Bookings");

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorPage(exchange, "Could not cancel the booking.");
        }
    }


    // ==========================================
    // ROOM SERVICE
    // ==========================================

    private static void roomService(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendErrorPage(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        int bookingId;
        int quantity;
        double price;

        try {
            bookingId = Integer.parseInt(
                    data.getOrDefault("booking_id", "0"));
            quantity = Integer.parseInt(
                    data.getOrDefault("quantity", "0"));
            price = Double.parseDouble(
                    data.getOrDefault("price", "0"));
        } catch (NumberFormatException e) {
            sendErrorPage(exchange, "Invalid service details.");
            return;
        }

        String serviceName =
                data.getOrDefault("service_name", "").trim();

        if (bookingId <= 0 || quantity <= 0 ||
                price <= 0 || serviceName.isEmpty()) {
            sendErrorPage(exchange, "Please enter valid service details.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            String check = """
                    SELECT status
                    FROM bookings
                    WHERE booking_id = ?
                    """;

            try (PreparedStatement ps = con.prepareStatement(check)) {
                ps.setInt(1, bookingId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        sendErrorPage(exchange, "Booking not found.");
                        return;
                    }

                    if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                        sendErrorPage(exchange,
                                "Room service can only be added to an active booking.");
                        return;
                    }
                }
            }

            double total = price * quantity;

            String insert = """
                    INSERT INTO room_services
                    (booking_id, service_name, quantity, price, total)
                    VALUES (?, ?, ?, ?, ?)
                    """;

            try (PreparedStatement ps = con.prepareStatement(insert)) {
                ps.setInt(1, bookingId);
                ps.setString(2, serviceName);
                ps.setInt(3, quantity);
                ps.setDouble(4, price);
                ps.setDouble(5, total);
                ps.executeUpdate();
            }

            sendActionPage(exchange,
                    "Room Service Added",
                    serviceName + " x " + quantity +
                            " has been added to Booking #" + bookingId +
                            " for Rs. " + String.format("%.2f", total),
                    "/bookings.html",
                    "Back to Bookings");

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorPage(exchange, "Could not add room service.");
        }
    }


    // ==========================================
    // ROOM SERVICE OPTIONS
    // ==========================================

    private static void serviceList(HttpExchange exchange)
            throws IOException {

        String json = """
                [
                    {"name":"Breakfast","price":300},
                    {"name":"Lunch","price":500},
                    {"name":"Dinner","price":600},
                    {"name":"Tea / Coffee","price":150},
                    {"name":"Extra Bed","price":800},
                    {"name":"Laundry","price":250},
                    {"name":"Room Cleaning","price":200}
                ]
                """;

        sendJson(exchange, json);
    }


    // ==========================================
    // CHECKOUT
    // ==========================================

    private static void checkout(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendErrorPage(exchange, "Invalid request.");
            return;
        }

        Map<String, String> data = parseForm(readBody(exchange));

        int bookingId;

        try {
            bookingId = Integer.parseInt(
                    data.getOrDefault("booking_id", "0"));
        } catch (NumberFormatException e) {
            sendErrorPage(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            int roomNumber;

            String check = """
                    SELECT room_number, status
                    FROM bookings
                    WHERE booking_id = ?
                    FOR UPDATE
                    """;

            try (PreparedStatement ps = con.prepareStatement(check)) {
                ps.setInt(1, bookingId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        sendErrorPage(exchange, "Booking not found.");
                        return;
                    }

                    if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                        con.rollback();
                        sendErrorPage(exchange,
                                "Only active bookings can be checked out.");
                        return;
                    }

                    roomNumber = rs.getInt("room_number");
                }
            }

            try (PreparedStatement ps = con.prepareStatement("""
                    UPDATE bookings
                    SET status='CHECKED_OUT',
                        checkout_date=CURRENT_TIMESTAMP
                    WHERE booking_id=?
                    """)) {
                ps.setInt(1, bookingId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE rooms SET available=TRUE WHERE room_number=?")) {
                ps.setInt(1, roomNumber);
                ps.executeUpdate();
            }

            con.commit();

            redirect(exchange, "/invoice?booking_id=" + bookingId);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorPage(exchange, "Checkout failed. Please try again.");
        }
    }


    // ==========================================
    // DIGITAL INVOICE
    // ==========================================

    private static void invoice(HttpExchange exchange)
            throws IOException {

        String rawQuery = exchange.getRequestURI().getRawQuery();

        if (rawQuery == null || rawQuery.isEmpty()) {
            sendErrorPage(exchange, "Booking ID is required.");
            return;
        }

        Map<String, String> params = parseForm(rawQuery);

        int bookingId;

        try {
            bookingId = Integer.parseInt(
                    params.getOrDefault("booking_id", "0"));
        } catch (NumberFormatException e) {
            sendErrorPage(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            String query = """
                    SELECT
                        b.booking_id,
                        c.name,
                        c.phone,
                        b.room_number,
                        r.room_type,
                        r.price,
                        b.days,
                        b.total,
                        b.status,
                        b.booking_date,
                        b.checkout_date
                    FROM bookings b
                    JOIN customers c ON b.customer_id=c.customer_id
                    JOIN rooms r ON b.room_number=r.room_number
                    WHERE b.booking_id=?
                    """;

            String name, phone, roomType, status;
            String bookingDate, checkoutDate;
            int roomNumber, days;
            double roomPrice, roomTotal;

            try (PreparedStatement ps = con.prepareStatement(query)) {

                ps.setInt(1, bookingId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        sendErrorPage(exchange, "Invoice not found.");
                        return;
                    }

                    name = rs.getString("name");
                    phone = rs.getString("phone");
                    roomNumber = rs.getInt("room_number");
                    roomType = rs.getString("room_type");
                    roomPrice = rs.getDouble("price");
                    days = rs.getInt("days");
                    roomTotal = rs.getDouble("total");
                    status = rs.getString("status");
                    bookingDate = rs.getString("booking_date");
                    checkoutDate = rs.getString("checkout_date");
                }
            }

            double serviceTotal = 0;
            StringBuilder serviceRows = new StringBuilder();

            String serviceQuery = """
                    SELECT service_name, quantity, price, total
                    FROM room_services
                    WHERE booking_id=?
                    ORDER BY service_id
                    """;

            try (PreparedStatement ps = con.prepareStatement(serviceQuery)) {

                ps.setInt(1, bookingId);

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {

                        double total = rs.getDouble("total");
                        serviceTotal += total;

                        serviceRows.append("""
                                <tr>
                                    <td>%s</td>
                                    <td>%d</td>
                                    <td>Rs. %.2f</td>
                                    <td>Rs. %.2f</td>
                                </tr>
                                """.formatted(
                                escapeHtml(rs.getString("service_name")),
                                rs.getInt("quantity"),
                                rs.getDouble("price"),
                                total));
                    }
                }
            }

            if (serviceRows.length() == 0) {
                serviceRows.append("""
                        <tr>
                            <td colspan="4" style="text-align:center;">
                                No room service charges
                            </td>
                        </tr>
                        """);
            }

            double grandTotal = roomTotal + serviceTotal;

            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Grand Stay Hotel - Invoice #%d</title>
                        <style>
                            body {
                                margin:0;
                                padding:30px;
                                background:#eef1f6;
                                font-family:Arial,sans-serif;
                                color:#1d2635;
                            }
                            .invoice {
                                max-width:900px;
                                margin:auto;
                                background:white;
                                padding:45px;
                                border-radius:14px;
                                box-shadow:0 10px 35px rgba(0,0,0,.12);
                            }
                            .top {
                                display:flex;
                                justify-content:space-between;
                                border-bottom:3px solid #c9a227;
                                padding-bottom:25px;
                            }
                            .hotel {
                                font-size:30px;
                                font-weight:bold;
                                color:#14213d;
                            }
                            .gold { color:#b28a19; }
                            .invoice-title { text-align:right; }
                            .invoice-title h1 { margin:0; }
                            .details {
                                display:grid;
                                grid-template-columns:1fr 1fr;
                                gap:20px;
                                margin:30px 0;
                            }
                            .box {
                                background:#f7f8fb;
                                padding:20px;
                                border-radius:10px;
                            }
                            .box h3 { color:#b28a19; margin-top:0; }
                            table {
                                width:100%%;
                                border-collapse:collapse;
                                margin:15px 0 30px;
                            }
                            th {
                                background:#14213d;
                                color:white;
                                padding:13px;
                                text-align:left;
                            }
                            td {
                                padding:13px;
                                border-bottom:1px solid #e5e7eb;
                            }
                            .summary {
                                width:330px;
                                margin-left:auto;
                            }
                            .summary div {
                                display:flex;
                                justify-content:space-between;
                                padding:10px 0;
                            }
                            .grand {
                                border-top:2px solid #14213d;
                                font-size:22px;
                                font-weight:bold;
                                color:#b28a19;
                            }
                            .thank {
                                text-align:center;
                                margin-top:35px;
                                padding-top:25px;
                                border-top:1px solid #ddd;
                            }
                            .buttons {
                                text-align:center;
                                margin-top:25px;
                            }
                            button, a {
                                display:inline-block;
                                background:#14213d;
                                color:white;
                                border:0;
                                border-radius:8px;
                                padding:12px 20px;
                                margin:5px;
                                text-decoration:none;
                                cursor:pointer;
                            }
                            @media print {
                                body { background:white; padding:0; }
                                .invoice { box-shadow:none; }
                                .buttons { display:none; }
                            }
                            @media(max-width:700px) {
                                .top { flex-direction:column; gap:20px; }
                                .invoice-title { text-align:left; }
                                .details { grid-template-columns:1fr; }
                                .summary { width:100%%; }
                            }
                        </style>
                    </head>
                    <body>
                    <div class="invoice">

                        <div class="top">
                            <div>
                                <div class="hotel">GRAND STAY HOTEL</div>
                                <div class="gold">
                                    Comfort • Luxury • Hospitality
                                </div>
                            </div>

                            <div class="invoice-title">
                                <h1>DIGITAL INVOICE</h1>
                                <p>Invoice #INV-%d</p>
                                <p>Status: %s</p>
                            </div>
                        </div>

                        <div class="details">
                            <div class="box">
                                <h3>Guest Details</h3>
                                <p><b>Name:</b> %s</p>
                                <p><b>Phone:</b> %s</p>
                            </div>

                            <div class="box">
                                <h3>Stay Details</h3>
                                <p><b>Room:</b> %d</p>
                                <p><b>Type:</b> %s</p>
                                <p><b>Nights:</b> %d</p>
                            </div>
                        </div>

                        <h2>Room Charges</h2>

                        <table>
                            <tr>
                                <th>Description</th>
                                <th>Nights</th>
                                <th>Rate</th>
                                <th>Total</th>
                            </tr>
                            <tr>
                                <td>%s Room</td>
                                <td>%d</td>
                                <td>Rs. %.2f</td>
                                <td>Rs. %.2f</td>
                            </tr>
                        </table>

                        <h2>Room Service</h2>

                        <table>
                            <tr>
                                <th>Service</th>
                                <th>Quantity</th>
                                <th>Price</th>
                                <th>Total</th>
                            </tr>
                            %s
                        </table>

                        <div class="summary">
                            <div>
                                <span>Room Charges</span>
                                <b>Rs. %.2f</b>
                            </div>
                            <div>
                                <span>Room Service</span>
                                <b>Rs. %.2f</b>
                            </div>
                            <div class="grand">
                                <span>Grand Total</span>
                                <b>Rs. %.2f</b>
                            </div>
                        </div>

                        <div class="box" style="margin-top:30px;">
                            <p><b>Booking Date:</b> %s</p>
                            <p><b>Checkout Date:</b> %s</p>
                        </div>

                        <div class="thank">
                            <b>Thank you for staying at Grand Stay Hotel!</b><br>
                            We hope to welcome you again.
                        </div>

                        <div class="buttons">
                            <button onclick="printInvoice()">
                                Print Invoice
                            </button>
                            <button onclick="saveInvoiceAsPDF()">
                                Save as PDF
                            </button>
                            <a href="/bookings.html">Back to Bookings</a>
                        </div>

                        <script>
                            function printInvoice() {
                                window.print();
                            }

                            function saveInvoiceAsPDF() {
                                window.location.href =
                                    "/invoice-pdf?booking_id=%d";
                            }
                        </script>

                    </div>
                    </body>
                    </html>
                    """.formatted(
                    bookingId,
                    bookingId,
                    escapeHtml(status),
                    escapeHtml(name),
                    escapeHtml(phone),
                    roomNumber,
                    escapeHtml(roomType),
                    days,
                    escapeHtml(roomType),
                    days,
                    roomPrice,
                    roomTotal,
                    serviceRows.toString(),
                    roomTotal,
                    serviceTotal,
                    grandTotal,
                    escapeHtml(bookingDate),
                    escapeHtml(checkoutDate == null ? "-" : checkoutDate),
                    bookingId
            );

            sendHtml(exchange, html);

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorPage(exchange, "Could not generate invoice.");
        }
    }



    // ==========================================
    // DOWNLOAD INVOICE AS PDF
    // ==========================================

    private static void invoicePdf(HttpExchange exchange)
            throws IOException {

        String rawQuery = exchange.getRequestURI().getRawQuery();

        if (rawQuery == null || rawQuery.isEmpty()) {
            sendErrorPage(exchange, "Booking ID is required.");
            return;
        }

        Map<String, String> params = parseForm(rawQuery);

        int bookingId;

        try {
            bookingId = Integer.parseInt(
                    params.getOrDefault("booking_id", "0"));
        } catch (NumberFormatException e) {
            sendErrorPage(exchange, "Invalid booking ID.");
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            String query = """
                    SELECT
                        b.booking_id,
                        c.name,
                        c.phone,
                        b.room_number,
                        r.room_type,
                        r.price,
                        b.days,
                        b.total,
                        b.status,
                        b.booking_date,
                        b.checkout_date
                    FROM bookings b
                    JOIN customers c
                        ON b.customer_id = c.customer_id
                    JOIN rooms r
                        ON b.room_number = r.room_number
                    WHERE b.booking_id = ?
                    """;

            String name;
            String phone;
            String roomType;
            String status;
            String bookingDate;
            String checkoutDate;

            int roomNumber;
            int days;

            double roomPrice;
            double roomTotal;

            try (PreparedStatement ps =
                         con.prepareStatement(query)) {

                ps.setInt(1, bookingId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        sendErrorPage(exchange,
                                "Invoice not found.");
                        return;
                    }

                    name = rs.getString("name");
                    phone = rs.getString("phone");
                    roomNumber = rs.getInt("room_number");
                    roomType = rs.getString("room_type");
                    roomPrice = rs.getDouble("price");
                    days = rs.getInt("days");
                    roomTotal = rs.getDouble("total");
                    status = rs.getString("status");
                    bookingDate = rs.getString("booking_date");
                    checkoutDate = rs.getString("checkout_date");
                }
            }

            double serviceTotal = 0;

            List<String> serviceNames = new ArrayList<>();
            List<Integer> serviceQuantities = new ArrayList<>();
            List<Double> servicePrices = new ArrayList<>();
            List<Double> serviceTotals = new ArrayList<>();

            String serviceQuery = """
                    SELECT service_name, quantity, price, total
                    FROM room_services
                    WHERE booking_id = ?
                    ORDER BY service_id
                    """;

            try (PreparedStatement ps =
                         con.prepareStatement(serviceQuery)) {

                ps.setInt(1, bookingId);

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {

                        String serviceName =
                                rs.getString("service_name");

                        int quantity =
                                rs.getInt("quantity");

                        double price =
                                rs.getDouble("price");

                        double total =
                                rs.getDouble("total");

                        serviceNames.add(serviceName);
                        serviceQuantities.add(quantity);
                        servicePrices.add(price);
                        serviceTotals.add(total);

                        serviceTotal += total;
                    }
                }
            }

            double grandTotal =
                    roomTotal + serviceTotal;

            List<String> lines = new ArrayList<>();

            lines.add("GRAND STAY HOTEL");
            lines.add("Comfort - Luxury - Hospitality");
            lines.add("");
            lines.add("DIGITAL INVOICE");
            lines.add("Invoice No: INV-" + bookingId);
            lines.add("Status: " + safePdfText(status));
            lines.add("");
            lines.add("GUEST DETAILS");
            lines.add("Name: " + safePdfText(name));
            lines.add("Phone: " + safePdfText(phone));
            lines.add("");
            lines.add("STAY DETAILS");
            lines.add("Room: " + roomNumber);
            lines.add("Type: " + safePdfText(roomType));
            lines.add("Nights: " + days);
            lines.add("");
            lines.add("ROOM CHARGES");
            lines.add("Room Type: " + safePdfText(roomType));
            lines.add("Rate per Night: Rs. " +
                    String.format(Locale.US, "%.2f", roomPrice));
            lines.add("Room Total: Rs. " +
                    String.format(Locale.US, "%.2f", roomTotal));
            lines.add("");
            lines.add("ROOM SERVICES");

            if (serviceNames.isEmpty()) {

                lines.add("No room service charges");

            } else {

                for (int i = 0;
                     i < serviceNames.size();
                     i++) {

                    lines.add(
                            safePdfText(serviceNames.get(i))
                                    + " x"
                                    + serviceQuantities.get(i)
                                    + " @ Rs. "
                                    + String.format(
                                            Locale.US,
                                            "%.2f",
                                            servicePrices.get(i))
                                    + " = Rs. "
                                    + String.format(
                                            Locale.US,
                                            "%.2f",
                                            serviceTotals.get(i))
                    );
                }
            }

            lines.add("");
            lines.add("PAYMENT SUMMARY");
            lines.add("Room Charges: Rs. " +
                    String.format(
                            Locale.US,
                            "%.2f",
                            roomTotal));
            lines.add("Room Service: Rs. " +
                    String.format(
                            Locale.US,
                            "%.2f",
                            serviceTotal));
            lines.add("GRAND TOTAL: Rs. " +
                    String.format(
                            Locale.US,
                            "%.2f",
                            grandTotal));
            lines.add("");
            lines.add("Booking Date: " +
                    safePdfText(bookingDate));
            lines.add("Checkout Date: " +
                    safePdfText(
                            checkoutDate == null
                                    ? "-"
                                    : checkoutDate));
            lines.add("");
            lines.add("Thank you for staying at Grand Stay Hotel!");
            lines.add("We hope to welcome you again.");

            byte[] pdf = createSimplePdf(lines);

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/pdf");

            exchange.getResponseHeaders().set(
                    "Content-Disposition",
                    "attachment; filename=\"Invoice-"
                            + bookingId
                            + ".pdf\"");

            exchange.getResponseHeaders().set(
                    "Cache-Control",
                    "no-store");

            exchange.sendResponseHeaders(
                    200,
                    pdf.length);

            try (OutputStream os =
                         exchange.getResponseBody()) {

                os.write(pdf);
            }

        } catch (Exception e) {

            e.printStackTrace();

            sendErrorPage(
                    exchange,
                    "Could not download invoice PDF.");
        }
    }


    private static String safePdfText(String value) {

        if (value == null)
            return "-";

        return value
                .replace("\\", "/")
                .replace("\r", " ")
                .replace("\n", " ");
    }


    private static byte[] createSimplePdf(
            List<String> lines)
            throws IOException {

        /*
         * This creates a real PDF using only Java's standard library.
         * It intentionally uses the built-in Helvetica font so no
         * additional PDF library is required.
         */

        final int pageWidth = 595;
        final int pageHeight = 842;

        final int marginLeft = 50;
        final int startY = 790;
        final int lineHeight = 16;

        int linesPerPage =
                (startY - 55) / lineHeight;

        List<List<String>> pages =
                new ArrayList<>();

        for (int i = 0; i < lines.size(); ) {

            List<String> pageLines =
                    new ArrayList<>();

            for (int j = 0;
                 j < linesPerPage && i < lines.size();
                 j++, i++) {

                pageLines.add(lines.get(i));
            }

            pages.add(pageLines);
        }

        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }

        int pageCount = pages.size();

        /*
         * Object numbering:
         *
         * 1 = Catalog
         * 2 = Pages
         * 3 = Font
         * then, for each page:
         * Page object
         * Content stream object
         */

        int totalObjects =
                3 + (pageCount * 2);

        ByteArrayOutputStream pdf =
                new ByteArrayOutputStream();

        List<Integer> offsets =
                new ArrayList<>();

        writePdf(pdf, "%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n");

        // Object 1 - Catalog
        offsets.add(pdf.size());
        writePdf(pdf,
                "1 0 obj\n"
                        + "<< /Type /Catalog "
                        + "/Pages 2 0 R >>\n"
                        + "endobj\n");

        // Object 2 - Pages
        offsets.add(pdf.size());

        StringBuilder kids =
                new StringBuilder();

        for (int i = 0; i < pageCount; i++) {

            int pageObject =
                    4 + (i * 2);

            kids.append(pageObject)
                    .append(" 0 R ");
        }

        writePdf(pdf,
                "2 0 obj\n"
                        + "<< /Type /Pages "
                        + "/Kids ["
                        + kids
                        + "] "
                        + "/Count "
                        + pageCount
                        + " >>\n"
                        + "endobj\n");

        // Object 3 - Helvetica font
        offsets.add(pdf.size());

        writePdf(pdf,
                "3 0 obj\n"
                        + "<< /Type /Font "
                        + "/Subtype /Type1 "
                        + "/BaseFont /Helvetica >>\n"
                        + "endobj\n");

        for (int pageIndex = 0;
             pageIndex < pageCount;
             pageIndex++) {

            int pageObject =
                    4 + (pageIndex * 2);

            int contentObject =
                    pageObject + 1;

            List<String> pageLines =
                    pages.get(pageIndex);

            StringBuilder stream =
                    new StringBuilder();

            stream.append("BT\n");
            stream.append("/F1 20 Tf\n");
            stream.append("50 790 Td\n");

            boolean firstLine = true;

            for (String line : pageLines) {

                String clean =
                        safePdfText(line);

                if (firstLine) {

                    firstLine = false;

                } else {

                    stream.append("0 -")
                            .append(lineHeight)
                            .append(" Td\n");
                }

                if (clean.equals("GRAND STAY HOTEL")) {

                    stream.append("/F1 22 Tf\n");

                } else if (
                        clean.equals("DIGITAL INVOICE")
                                || clean.equals("GUEST DETAILS")
                                || clean.equals("STAY DETAILS")
                                || clean.equals("ROOM CHARGES")
                                || clean.equals("ROOM SERVICES")
                                || clean.equals("PAYMENT SUMMARY")) {

                    stream.append("/F1 14 Tf\n");

                } else if (
                        clean.startsWith("GRAND TOTAL:")) {

                    stream.append("/F1 14 Tf\n");

                } else {

                    stream.append("/F1 10 Tf\n");
                }

                stream.append("(")
                        .append(pdfEscape(clean))
                        .append(") Tj\n");
            }

            stream.append("ET\n");

            byte[] streamBytes =
                    stream.toString()
                            .getBytes(StandardCharsets.US_ASCII);

            // Page object
            offsets.add(pdf.size());

            writePdf(pdf,
                    pageObject
                            + " 0 obj\n"
                            + "<< /Type /Page "
                            + "/Parent 2 0 R "
                            + "/MediaBox [0 0 "
                            + pageWidth
                            + " "
                            + pageHeight
                            + "] "
                            + "/Resources << "
                            + "/Font << /F1 3 0 R >> "
                            + ">> "
                            + "/Contents "
                            + contentObject
                            + " 0 R >>\n"
                            + "endobj\n");

            // Content stream object
            offsets.add(pdf.size());

            writePdf(pdf,
                    contentObject
                            + " 0 obj\n"
                            + "<< /Length "
                            + streamBytes.length
                            + " >>\n"
                            + "stream\n");

            pdf.write(streamBytes);

            writePdf(pdf,
                    "endstream\n"
                            + "endobj\n");
        }

        int xrefOffset = pdf.size();

        writePdf(pdf,
                "xref\n"
                        + "0 "
                        + (totalObjects + 1)
                        + "\n");

        writePdf(pdf,
                "0000000000 65535 f \n");

        for (int offset : offsets) {

            writePdf(pdf,
                    String.format(
                            Locale.US,
                            "%010d 00000 n \n",
                            offset));
        }

        writePdf(pdf,
                "trailer\n"
                        + "<< /Size "
                        + (totalObjects + 1)
                        + " /Root 1 0 R >>\n"
                        + "startxref\n"
                        + xrefOffset
                        + "\n"
                        + "%%EOF\n");

        return pdf.toByteArray();
    }


    private static String pdfEscape(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }


    private static void writePdf(
            OutputStream output,
            String value)
            throws IOException {

        output.write(
                value.getBytes(
                        StandardCharsets.US_ASCII));
    }


    // ==========================================
    // ACTION PAGE
    // ==========================================

    private static void sendActionPage(
            HttpExchange exchange,
            String title,
            String message,
            String link,
            String linkText) throws IOException {

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>%s</title>
                    <link rel="stylesheet" href="/css/style.css">
                </head>
                <body>
                <div class="message-page">
                    <div class="success-card">
                        <div class="success-icon">✓</div>
                        <h1>%s</h1>
                        <p class="success-text">%s</p>
                        <div class="message-buttons">
                            <a href="%s">%s</a>
                            <a href="/dashboard.html">Dashboard</a>
                        </div>
                    </div>
                </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(title),
                escapeHtml(message),
                link,
                escapeHtml(linkText));

        sendHtml(exchange, html);
    }


    // ==========================================
    // SERVE FILE
    // ==========================================

    private static void serveFile(
            HttpExchange exchange,
            String fileName)
            throws IOException {

        File file =
                new File(
                        "webapp",
                        fileName
                );

        if (!file.exists()) {

            String error =
                    "File not found: "
                    + fileName;

            exchange.sendResponseHeaders(
                    404,
                    error.length()
            );

            try (OutputStream os =
                         exchange.getResponseBody()) {

                os.write(
                        error.getBytes(
                                StandardCharsets.UTF_8));
            }

            return;
        }

        byte[] data =
                java.nio.file.Files.readAllBytes(
                        file.toPath());

        String contentType =
                "text/html; charset=UTF-8";

        if (fileName.endsWith(".css")) {

            contentType =
                    "text/css; charset=UTF-8";
        }

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        contentType
                );

        exchange.sendResponseHeaders(
                200,
                data.length
        );

        try (OutputStream os =
                     exchange.getResponseBody()) {

            os.write(data);
        }
    }


    // ==========================================
    // READ BODY
    // ==========================================

    private static String readBody(
            HttpExchange exchange)
            throws IOException {

        try (InputStream is =
                     exchange.getRequestBody();

             ByteArrayOutputStream buffer =
                     new ByteArrayOutputStream()) {

            byte[] data =
                    new byte[1024];

            int n;

            while ((n = is.read(data)) != -1) {

                buffer.write(
                        data,
                        0,
                        n
                );
            }

            return buffer.toString(
                    StandardCharsets.UTF_8
            );
        }
    }


    // ==========================================
    // FORM PARSER
    // ==========================================

    private static Map<String, String>
    parseForm(String body) {

        Map<String, String> map =
                new HashMap<>();

        if (body == null || body.isEmpty()) {

            return map;
        }

        String[] pairs =
                body.split("&");

        for (String pair : pairs) {

            String[] parts =
                    pair.split("=", 2);

            String key =
                    URLDecoder.decode(
                            parts[0],
                            StandardCharsets.UTF_8
                    );

            String value =
                    parts.length > 1
                    ? URLDecoder.decode(
                            parts[1],
                            StandardCharsets.UTF_8)
                    : "";

            map.put(key, value);
        }

        return map;
    }


    // ==========================================
    // REDIRECT
    // ==========================================

    private static void redirect(
            HttpExchange exchange,
            String location)
            throws IOException {

        exchange.getResponseHeaders()
                .set("Location", location);

        exchange.sendResponseHeaders(
                302,
                -1
        );

        exchange.close();
    }


    // ==========================================
    // SEND HTML
    // ==========================================

    private static void sendHtml(
            HttpExchange exchange,
            String html)
            throws IOException {

        byte[] data =
                html.getBytes(
                        StandardCharsets.UTF_8);

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/html; charset=UTF-8"
                );

        exchange.sendResponseHeaders(
                200,
                data.length
        );

        try (OutputStream os =
                     exchange.getResponseBody()) {

            os.write(data);
        }
    }


    // ==========================================
    // SEND JSON
    // ==========================================

    private static void sendJson(
            HttpExchange exchange,
            String json)
            throws IOException {

        byte[] data =
                json.getBytes(
                        StandardCharsets.UTF_8);

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

        exchange.sendResponseHeaders(
                200,
                data.length
        );

        try (OutputStream os =
                     exchange.getResponseBody()) {

            os.write(data);
        }
    }


    // ==========================================
    // ERROR PAGE
    // ==========================================

    private static void sendErrorPage(
            HttpExchange exchange,
            String message)
            throws IOException {

        String html = """
                <!DOCTYPE html>
                <html>

                <head>

                    <title>Error</title>

                    <link rel="stylesheet"
                          href="/css/style.css">

                </head>

                <body>

                <div class="message-page">

                    <div class="message-card">

                        <div class="message-icon">
                            !
                        </div>

                        <h1>Something went wrong</h1>

                        <p>%s</p>

                        <a href="/booking.html">
                            Back to Booking
                        </a>

                    </div>

                </div>

                </body>

                </html>
                """.formatted(
                        escapeHtml(message)
                );

        sendHtml(exchange, html);
    }


    // ==========================================
    // JSON ESCAPE
    // ==========================================

    private static String jsonEscape(
            String text) {

        if (text == null)
            return "";

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }


    // ==========================================
    // HTML ESCAPE
    // ==========================================

    private static String escapeHtml(
            String text) {

        if (text == null)
            return "";

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}