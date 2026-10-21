package com.hotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HotelServer {

    private static final Path WEB =
            Paths.get("webapp")
                    .toAbsolutePath()
                    .normalize();

    private static final Map<String, Double> SERVICES =
            new LinkedHashMap<>();

    static {

        SERVICES.put("Breakfast", 300.0);
        SERVICES.put("Lunch", 500.0);
        SERVICES.put("Dinner", 600.0);
        SERVICES.put("Tea / Coffee", 150.0);
        SERVICES.put("Extra Bed", 800.0);
        SERVICES.put("Laundry", 250.0);
        SERVICES.put("Room Cleaning", 200.0);
    }


    public static void main(String[] args)
            throws Exception {

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(8080),
                        0
                );


        // PAGES

        server.createContext(
                "/",
                exchange ->
                        serveFile(exchange, "index.html")
        );

        server.createContext(
                "/login",
                HotelServer::login
        );

        server.createContext(
                "/dashboard.html",
                exchange ->
                        serveFile(
                                exchange,
                                "dashboard.html"
                        )
        );

        server.createContext(
                "/rooms.html",
                exchange ->
                        serveFile(
                                exchange,
                                "rooms.html"
                        )
        );

        server.createContext(
                "/booking.html",
                exchange ->
                        serveFile(
                                exchange,
                                "booking.html"
                        )
        );

        server.createContext(
                "/bookings.html",
                exchange ->
                        serveFile(
                                exchange,
                                "bookings.html"
                        )
        );

        server.createContext(
                "/settings.html",
                exchange ->
                        serveFile(
                                exchange,
                                "settings.html"
                        )
        );

        server.createContext(
                "/css/style.css",
                exchange ->
                        serveFile(
                                exchange,
                                "css/style.css"
                        )
        );


        // API ROUTES

        server.createContext(
                "/dashboard-data",
                HotelServer::dashboardData
        );

        server.createContext(
                "/all-rooms",
                HotelServer::allRooms
        );

        server.createContext(
                "/available-rooms",
                HotelServer::availableRooms
        );

        server.createContext(
                "/booking-history",
                HotelServer::bookingHistory
        );

        server.createContext(
                "/book",
                HotelServer::book
        );

        server.createContext(
                "/cancel-booking",
                HotelServer::cancelBooking
        );

        server.createContext(
                "/checkout",
                HotelServer::checkout
        );

        server.createContext(
                "/room-service",
                HotelServer::roomService
        );

        server.createContext(
                "/service-list",
                HotelServer::serviceList
        );

        server.createContext(
                "/invoice",
                HotelServer::invoice
        );

        server.createContext(
                "/invoice-pdf",
                HotelServer::invoicePdf
        );

        server.createContext(
                "/add-floor",
                HotelServer::addFloor
        );

        server.createContext(
                "/delete-floor",
                HotelServer::deleteFloor
        );


        server.start();


        System.out.println();
        System.out.println(
                "=========================================="
        );
        System.out.println(
                "          GRAND STAY HOTEL"
        );
        System.out.println(
                "=========================================="
        );
        System.out.println(
                "Server running at:"
        );
        System.out.println(
                "http://localhost:8080"
        );
        System.out.println();
        System.out.println(
                "24 Rooms | 8 Floors"
        );
        System.out.println(
                "2 Single + 1 Deluxe per floor"
        );
        System.out.println();
        System.out.println(
                "Press Ctrl+C to stop the server."
        );
        System.out.println(
                "=========================================="
        );
    }


    // ==================================================
    // LOGIN
    // ==================================================

    private static void login(
            HttpExchange exchange)
            throws IOException {

        if (!"POST".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            redirect(exchange, "/");
            return;
        }


        Map<String, String> data =
                parseForm(readBody(exchange));


        String username =
                data.getOrDefault(
                        "username",
                        ""
                );

        String password =
                data.getOrDefault(
                        "password",
                        ""
                );


        if (
                username.equals("admin")
                        &&
                password.equals("admin123")
        ) {

            redirect(
                    exchange,
                    "/dashboard.html"
            );

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


    // ==================================================
    // DASHBOARD DATA
    // ==================================================

    private static void dashboardData(
            HttpExchange exchange)
            throws IOException {

        if (!"GET".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            sendJson(
                    exchange,
                    "{\"error\":\"Invalid request\"}"
            );

            return;
        }


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            int totalRooms =
                    scalarInt(
                            con,
                            "SELECT COUNT(*) FROM rooms"
                    );


            int availableRooms =
                    scalarInt(
                            con,
                            "SELECT COUNT(*) " +
                            "FROM rooms " +
                            "WHERE available=TRUE"
                    );


            int bookedRooms =
                    totalRooms -
                    availableRooms;


            int totalBookings =
                    scalarInt(
                            con,
                            "SELECT COUNT(*) " +
                            "FROM bookings"
                    );


            double roomRevenue =
                    scalarDouble(
                            con,
                            "SELECT COALESCE(" +
                            "SUM(total),0) " +
                            "FROM bookings " +
                            "WHERE status <> " +
                            "'CANCELLED'"
                    );


            double serviceRevenue =
                    scalarDouble(
                            con,
                            "SELECT COALESCE(" +
                            "SUM(rs.total),0) " +
                            "FROM room_services rs " +
                            "JOIN bookings b " +
                            "ON rs.booking_id=" +
                            "b.booking_id " +
                            "WHERE b.status <> " +
                            "'CANCELLED'"
                    );


            double revenue =
                    roomRevenue +
                    serviceRevenue;


            StringBuilder json =
                    new StringBuilder();


            json.append("{");

            json.append(
                    "\"totalRooms\":"
            ).append(totalRooms).append(",");

            json.append(
                    "\"availableRooms\":"
            ).append(availableRooms).append(",");

            json.append(
                    "\"bookedRooms\":"
            ).append(bookedRooms).append(",");

            json.append(
                    "\"totalBookings\":"
            ).append(totalBookings).append(",");

            json.append(
                    "\"revenue\":"
            ).append(revenue).append(",");


            // ROOMS

            json.append("\"rooms\":[");


            String roomQuery =
                    "SELECT room_number," +
                    "floor_number," +
                    "room_type," +
                    "price," +
                    "available " +
                    "FROM rooms " +
                    "ORDER BY room_number";


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    roomQuery
                            );

                    ResultSet rs =
                            ps.executeQuery()
            ) {

                boolean first = true;


                while (rs.next()) {

                    if (!first) {
                        json.append(",");
                    }

                    first = false;


                    json.append(
                            String.format(
                                    Locale.US,

                                    "{\"roomNumber\":%d," +
                                    "\"floor\":%d," +
                                    "\"roomType\":\"%s\"," +
                                    "\"price\":%.2f," +
                                    "\"available\":%s}",

                                    rs.getInt(
                                            "room_number"
                                    ),

                                    rs.getInt(
                                            "floor_number"
                                    ),

                                    jsonEscape(
                                            rs.getString(
                                                    "room_type"
                                            )
                                    ),

                                    rs.getDouble(
                                            "price"
                                    ),

                                    rs.getBoolean(
                                            "available"
                                    )
                            )
                    );
                }
            }


            json.append("],");


            // RECENT BOOKINGS

            json.append(
                    "\"recentBookings\":["
            );


            String recentQuery =
                    "SELECT " +
                    "b.booking_id," +
                    "c.name," +
                    "b.room_number," +
                    "r.room_type," +
                    "b.days," +
                    "b.total," +
                    "b.status," +
                    "b.booking_date," +

                    "COALESCE((" +
                    "SELECT SUM(rs.total) " +
                    "FROM room_services rs " +
                    "WHERE rs.booking_id=" +
                    "b.booking_id" +
                    "),0) AS service_total " +

                    "FROM bookings b " +

                    "JOIN customers c " +
                    "ON b.customer_id=" +
                    "c.customer_id " +

                    "JOIN rooms r " +
                    "ON b.room_number=" +
                    "r.room_number " +

                    "ORDER BY b.booking_id DESC " +
                    "LIMIT 5";


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    recentQuery
                            );

                    ResultSet rs =
                            ps.executeQuery()
            ) {

                boolean first = true;


                while (rs.next()) {

                    if (!first) {
                        json.append(",");
                    }

                    first = false;


                    json.append(
                            String.format(
                                    Locale.US,

                                    "{\"bookingId\":%d," +
                                    "\"name\":\"%s\"," +
                                    "\"room\":%d," +
                                    "\"roomType\":\"%s\"," +
                                    "\"days\":%d," +
                                    "\"total\":%.2f," +
                                    "\"serviceTotal\":%.2f," +
                                    "\"status\":\"%s\"," +
                                    "\"date\":\"%s\"}",

                                    rs.getInt(
                                            "booking_id"
                                    ),

                                    jsonEscape(
                                            rs.getString(
                                                    "name"
                                            )
                                    ),

                                    rs.getInt(
                                            "room_number"
                                    ),

                                    jsonEscape(
                                            rs.getString(
                                                    "room_type"
                                            )
                                    ),

                                    rs.getInt("days"),

                                    rs.getDouble("total"),

                                    rs.getDouble(
                                            "service_total"
                                    ),

                                    jsonEscape(
                                            rs.getString(
                                                    "status"
                                            )
                                    ),

                                    jsonEscape(
                                            formatDate(
                                                    rs.getTimestamp(
                                                            "booking_date"
                                                    )
                                            )
                                    )
                            )
                    );
                }
            }


            json.append("]");

            json.append("}");


            sendJson(
                    exchange,
                    json.toString()
            );


        } catch (Exception e) {

            sendJson(
                    exchange,
                    "{\"error\":\"" +
                    jsonEscape(
                            e.getMessage()
                    ) +
                    "\"}"
            );
        }
    }


    // ==================================================
    // ALL ROOMS
    // ==================================================

    private static void allRooms(
            HttpExchange exchange)
            throws IOException {

        roomList(
                exchange,
                false
        );
    }


    // ==================================================
    // AVAILABLE ROOMS
    // ==================================================

    private static void availableRooms(
            HttpExchange exchange)
            throws IOException {

        roomList(
                exchange,
                true
        );
    }


    private static void roomList(
            HttpExchange exchange,
            boolean onlyAvailable)
            throws IOException {


        String query =
                "SELECT room_number," +
                "floor_number," +
                "room_type," +
                "price," +
                "available " +
                "FROM rooms ";


        if (onlyAvailable) {

            query +=
                    "WHERE available=TRUE ";
        }


        query +=
                "ORDER BY floor_number," +
                "room_number";


        try (
                Connection con =
                        DatabaseConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(query);

                ResultSet rs =
                        ps.executeQuery()
        ) {


            StringBuilder json =
                    new StringBuilder("[");

            boolean first = true;


            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                first = false;


                json.append(
                        String.format(
                                Locale.US,

                                "{\"roomNumber\":%d," +
                                "\"floor\":%d," +
                                "\"roomType\":\"%s\"," +
                                "\"price\":%.2f," +
                                "\"available\":%s}",

                                rs.getInt(
                                        "room_number"
                                ),

                                rs.getInt(
                                        "floor_number"
                                ),

                                jsonEscape(
                                        rs.getString(
                                                "room_type"
                                        )
                                ),

                                rs.getDouble(
                                        "price"
                                ),

                                rs.getBoolean(
                                        "available"
                                )
                        )
                );
            }


            json.append("]");


            sendJson(
                    exchange,
                    json.toString()
            );


        } catch (Exception e) {

            sendJson(
                    exchange,
                    "{\"error\":\"" +
                    jsonEscape(
                            e.getMessage()
                    ) +
                    "\"}"
            );
        }
    }


    // ==================================================
    // BOOKING HISTORY
    // ==================================================

    private static void bookingHistory(
            HttpExchange exchange)
            throws IOException {


        String query =
                "SELECT " +

                "b.booking_id," +
                "c.name," +
                "c.phone," +
                "b.room_number," +
                "r.room_type," +
                "b.days," +
                "b.total," +
                "b.status," +
                "b.booking_date," +
                "b.checkout_date," +

                "COALESCE((" +
                "SELECT SUM(rs.total) " +
                "FROM room_services rs " +
                "WHERE rs.booking_id=" +
                "b.booking_id" +
                "),0) AS service_total " +

                "FROM bookings b " +

                "JOIN customers c " +
                "ON b.customer_id=" +
                "c.customer_id " +

                "JOIN rooms r " +
                "ON b.room_number=" +
                "r.room_number " +

                "ORDER BY b.booking_id DESC";


        try (
                Connection con =
                        DatabaseConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(query);

                ResultSet rs =
                        ps.executeQuery()
        ) {


            StringBuilder json =
                    new StringBuilder("[");

            boolean first = true;


            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                first = false;


                String bookingDate =
                        formatDate(
                                rs.getTimestamp(
                                        "booking_date"
                                )
                        );


                String checkoutDate = "";


                if (
                        rs.getTimestamp(
                                "checkout_date"
                        ) != null
                ) {

                    checkoutDate =
                            formatDate(
                                    rs.getTimestamp(
                                            "checkout_date"
                                    )
                            );
                }


                json.append(
                        String.format(
                                Locale.US,

                                "{\"bookingId\":%d," +
                                "\"name\":\"%s\"," +
                                "\"phone\":\"%s\"," +
                                "\"roomNumber\":%d," +
                                "\"roomType\":\"%s\"," +
                                "\"days\":%d," +
                                "\"total\":%.2f," +
                                "\"status\":\"%s\"," +
                                "\"bookingDate\":\"%s\"," +
                                "\"checkoutDate\":\"%s\"," +
                                "\"serviceTotal\":%.2f}",

                                rs.getInt(
                                        "booking_id"
                                ),

                                jsonEscape(
                                        rs.getString(
                                                "name"
                                        )
                                ),

                                jsonEscape(
                                        rs.getString(
                                                "phone"
                                        )
                                ),

                                rs.getInt(
                                        "room_number"
                                ),

                                jsonEscape(
                                        rs.getString(
                                                "room_type"
                                        )
                                ),

                                rs.getInt("days"),

                                rs.getDouble("total"),

                                jsonEscape(
                                        rs.getString(
                                                "status"
                                        )
                                ),

                                jsonEscape(
                                        bookingDate
                                ),

                                jsonEscape(
                                        checkoutDate
                                ),

                                rs.getDouble(
                                        "service_total"
                                )
                        )
                );
            }


            json.append("]");


            sendJson(
                    exchange,
                    json.toString()
            );


        } catch (Exception e) {

            sendJson(
                    exchange,
                    "{\"error\":\"" +
                    jsonEscape(
                            e.getMessage()
                    ) +
                    "\"}"
            );
        }
    }


    // ==================================================
    // CREATE BOOKING
    // ==================================================

    private static void book(
            HttpExchange exchange)
            throws IOException {


        if (!"POST".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            redirect(
                    exchange,
                    "/booking.html"
            );

            return;
        }


        Map<String, String> data =
                parseForm(
                        readBody(exchange)
                );


        String name =
                data.getOrDefault(
                        "name",
                        ""
                ).trim();


        String phone =
                data.getOrDefault(
                        "phone",
                        ""
                ).trim();


        int room =
                intValue(
                        data.getOrDefault(
                                "roomNumber",
                                data.getOrDefault(
                                        "room",
                                        "0"
                                )
                        ),
                        0
                );


        int days =
                Math.max(
                        1,
                        intValue(
                                data.get("days"),
                                1
                        )
                );


        if (
                name.isBlank()
                        ||
                phone.isBlank()
                        ||
                room <= 0
        ) {

            sendError(
                    exchange,
                    "Please fill all booking details."
            );

            return;
        }


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            con.setAutoCommit(false);


            try {


                double price;


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "SELECT price,available " +
                                        "FROM rooms " +
                                        "WHERE room_number=? " +
                                        "FOR UPDATE"
                                )
                ) {

                    ps.setInt(1, room);


                    try (
                            ResultSet rs =
                                    ps.executeQuery()
                    ) {

                        if (!rs.next()) {

                            throw new IllegalArgumentException(
                                    "Room does not exist."
                            );
                        }


                        if (
                                !rs.getBoolean(
                                        "available"
                                )
                        ) {

                            throw new IllegalArgumentException(
                                    "Room is already booked."
                            );
                        }


                        price =
                                rs.getDouble(
                                        "price"
                                );
                    }
                }


                int customerId;


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "INSERT INTO customers" +
                                        "(name,phone)" +
                                        "VALUES(?,?)",
                                        Statement.RETURN_GENERATED_KEYS
                                )
                ) {

                    ps.setString(
                            1,
                            name
                    );

                    ps.setString(
                            2,
                            phone
                    );

                    ps.executeUpdate();


                    try (
                            ResultSet rs =
                                    ps.getGeneratedKeys()
                    ) {

                        rs.next();

                        customerId =
                                rs.getInt(1);
                    }
                }


                double total =
                        price * days;


                int bookingId;


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "INSERT INTO bookings" +
                                        "(customer_id,room_number," +
                                        "days,total,status)" +
                                        "VALUES(?,?,?,?, 'ACTIVE')",
                                        Statement.RETURN_GENERATED_KEYS
                                )
                ) {

                    ps.setInt(
                            1,
                            customerId
                    );

                    ps.setInt(
                            2,
                            room
                    );

                    ps.setInt(
                            3,
                            days
                    );

                    ps.setDouble(
                            4,
                            total
                    );

                    ps.executeUpdate();


                    try (
                            ResultSet rs =
                                    ps.getGeneratedKeys()
                    ) {

                        rs.next();

                        bookingId =
                                rs.getInt(1);
                    }
                }


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "UPDATE rooms " +
                                        "SET available=FALSE " +
                                        "WHERE room_number=?"
                                )
                ) {

                    ps.setInt(
                            1,
                            room
                    );

                    ps.executeUpdate();
                }


                con.commit();


                redirect(
                        exchange,
                        "/bookings.html?success=" +
                        urlEncode(
                                "Booking created successfully. "
                                + "Booking ID: "
                                + bookingId
                        )
                );


            } catch (Exception e) {

                con.rollback();

                sendError(
                        exchange,
                        e.getMessage()
                );
            }


        } catch (Exception e) {

            sendError(
                    exchange,
                    e.getMessage()
            );
        }
    }


    // ==================================================
    // CANCEL BOOKING
    // ==================================================

    private static void cancelBooking(
            HttpExchange exchange)
            throws IOException {


        if (!"POST".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            sendError(
                    exchange,
                    "Invalid request."
            );

            return;
        }


        Map<String, String> data =
                parseForm(
                        readBody(exchange)
                );


        int bookingId =
                intValue(
                        data.get("bookingId"),
                        0
                );


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            con.setAutoCommit(false);


            try {


                int roomNumber;


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "SELECT room_number,status " +
                                        "FROM bookings " +
                                        "WHERE booking_id=? " +
                                        "FOR UPDATE"
                                )
                ) {

                    ps.setInt(
                            1,
                            bookingId
                    );


                    try (
                            ResultSet rs =
                                    ps.executeQuery()
                    ) {

                        if (!rs.next()) {

                            throw new IllegalArgumentException(
                                    "Booking not found."
                            );
                        }


                        if (
                                !"ACTIVE".equals(
                                        rs.getString(
                                                "status"
                                        )
                                )
                        ) {

                            throw new IllegalArgumentException(
                                    "Only active bookings can be cancelled."
                            );
                        }


                        roomNumber =
                                rs.getInt(
                                        "room_number"
                                );
                    }
                }


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "UPDATE bookings " +
                                        "SET status='CANCELLED' " +
                                        "WHERE booking_id=?"
                                )
                ) {

                    ps.setInt(
                            1,
                            bookingId
                    );

                    ps.executeUpdate();
                }


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "UPDATE rooms " +
                                        "SET available=TRUE " +
                                        "WHERE room_number=?"
                                )
                ) {

                    ps.setInt(
                            1,
                            roomNumber
                    );

                    ps.executeUpdate();
                }


                con.commit();


                redirect(
                        exchange,
                        "/bookings.html?success=" +
                        urlEncode(
                                "Booking cancelled successfully."
                        )
                );


            } catch (Exception e) {

                con.rollback();

                sendError(
                        exchange,
                        e.getMessage()
                );
            }


        } catch (Exception e) {

            sendError(
                    exchange,
                    e.getMessage()
            );
        }
    }


    // ==================================================
    // CHECKOUT
    // ==================================================

    private static void checkout(
            HttpExchange exchange)
            throws IOException {


        if (!"POST".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            sendError(
                    exchange,
                    "Invalid request."
            );

            return;
        }


        Map<String, String> data =
                parseForm(
                        readBody(exchange)
                );


        int bookingId =
                intValue(
                        data.get("bookingId"),
                        0
                );


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            con.setAutoCommit(false);


            try {


                int roomNumber;


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "SELECT room_number,status " +
                                        "FROM bookings " +
                                        "WHERE booking_id=? " +
                                        "FOR UPDATE"
                                )
                ) {

                    ps.setInt(
                            1,
                            bookingId
                    );


                    try (
                            ResultSet rs =
                                    ps.executeQuery()
                    ) {

                        if (!rs.next()) {

                            throw new IllegalArgumentException(
                                    "Booking not found."
                            );
                        }


                        if (
                                !"ACTIVE".equals(
                                        rs.getString(
                                                "status"
                                        )
                                )
                        ) {

                            throw new IllegalArgumentException(
                                    "Only active bookings can be checked out."
                            );
                        }


                        roomNumber =
                                rs.getInt(
                                        "room_number"
                                );
                    }
                }


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "UPDATE bookings " +
                                        "SET status='CHECKED_OUT'," +
                                        "checkout_date=NOW() " +
                                        "WHERE booking_id=?"
                                )
                ) {

                    ps.setInt(
                            1,
                            bookingId
                    );

                    ps.executeUpdate();
                }


                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "UPDATE rooms " +
                                        "SET available=TRUE " +
                                        "WHERE room_number=?"
                                )
                ) {

                    ps.setInt(
                            1,
                            roomNumber
                    );

                    ps.executeUpdate();
                }


                con.commit();


                redirect(
                        exchange,
                        "/invoice?booking_id=" +
                        bookingId
                );


            } catch (Exception e) {

                con.rollback();

                sendError(
                        exchange,
                        e.getMessage()
                );
            }


        } catch (Exception e) {

            sendError(
                    exchange,
                    e.getMessage()
            );
        }
    }


    // ==================================================
    // ROOM SERVICE
    // ==================================================

    private static void roomService(
            HttpExchange exchange)
            throws IOException {


        if (!"POST".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            sendError(
                    exchange,
                    "Invalid request."
            );

            return;
        }


        Map<String, String> data =
                parseForm(
                        readBody(exchange)
                );


        int bookingId =
                intValue(
                        data.get("bookingId"),
                        0
                );


        int quantity =
                Math.max(
                        1,
                        intValue(
                                data.get("quantity"),
                                1
                        )
                );


        String serviceName =
                data.getOrDefault(
                        "serviceName",
                        ""
                ).trim();


        if (
                bookingId <= 0
                        ||
                !SERVICES.containsKey(
                        serviceName
                )
        ) {

            sendError(
                    exchange,
                    "Invalid service."
            );

            return;
        }


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            String status;


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT status " +
                                    "FROM bookings " +
                                    "WHERE booking_id=?"
                            )
            ) {

                ps.setInt(
                        1,
                        bookingId
                );


                try (
                        ResultSet rs =
                                ps.executeQuery()
                ) {

                    if (!rs.next()) {

                        sendError(
                                exchange,
                                "Booking not found."
                        );

                        return;
                    }


                    status =
                            rs.getString(
                                    "status"
                            );
                }
            }


            if (
                    !"ACTIVE".equals(status)
            ) {

                sendError(
                        exchange,
                        "Services can only be added to active bookings."
                );

                return;
            }


            double price =
                    SERVICES.get(
                            serviceName
                    );


            double total =
                    price * quantity;


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "INSERT INTO room_services" +
                                    "(booking_id,service_name," +
                                    "quantity,price,total)" +
                                    "VALUES(?,?,?,?,?)"
                            )
            ) {

                ps.setInt(
                        1,
                        bookingId
                );

                ps.setString(
                        2,
                        serviceName
                );

                ps.setInt(
                        3,
                        quantity
                );

                ps.setDouble(
                        4,
                        price
                );

                ps.setDouble(
                        5,
                        total
                );

                ps.executeUpdate();
            }


            redirect(
                    exchange,
                    "/bookings.html?success=" +
                    urlEncode(
                            "Service added successfully."
                    )
            );


        } catch (Exception e) {

            sendError(
                    exchange,
                    e.getMessage()
            );
        }
    }


    // ==================================================
    // SERVICE LIST
    // ==================================================

    private static void serviceList(
            HttpExchange exchange)
            throws IOException {


        Map<String, String> query =
                parseForm(
                        exchange.getRequestURI()
                                .getRawQuery()
                );


        int bookingId =
                intValue(
                        query.get("booking_id"),
                        0
                );


        try (
                Connection con =
                        DatabaseConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT service_name," +
                                "quantity,price,total," +
                                "service_date " +
                                "FROM room_services " +
                                "WHERE booking_id=? " +
                                "ORDER BY service_id DESC"
                        )
        ) {


            ps.setInt(
                    1,
                    bookingId
            );


            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {


                StringBuilder json =
                        new StringBuilder("[");

                boolean first = true;


                while (rs.next()) {

                    if (!first) {
                        json.append(",");
                    }

                    first = false;


                    json.append(
                            String.format(
                                    Locale.US,

                                    "{\"name\":\"%s\"," +
                                    "\"quantity\":%d," +
                                    "\"price\":%.2f," +
                                    "\"total\":%.2f," +
                                    "\"date\":\"%s\"}",

                                    jsonEscape(
                                            rs.getString(
                                                    "service_name"
                                            )
                                    ),

                                    rs.getInt(
                                            "quantity"
                                    ),

                                    rs.getDouble(
                                            "price"
                                    ),

                                    rs.getDouble(
                                            "total"
                                    ),

                                    jsonEscape(
                                            formatDate(
                                                    rs.getTimestamp(
                                                            "service_date"
                                                    )
                                            )
                                    )
                            )
                    );
                }


                json.append("]");


                sendJson(
                        exchange,
                        json.toString()
                );
            }


        } catch (Exception e) {

            sendJson(
                    exchange,
                    "{\"error\":\"" +
                    jsonEscape(
                            e.getMessage()
                    ) +
                    "\"}"
            );
        }
    }


    // ==================================================
    // INVOICE
    // ==================================================

    private static void invoice(
            HttpExchange exchange)
            throws IOException {


        Map<String, String> query =
                parseForm(
                        exchange.getRequestURI()
                                .getRawQuery()
                );


        int bookingId =
                intValue(
                        query.get("booking_id"),
                        0
                );


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            String name = "";
            String phone = "";
            String roomType = "";
            String status = "";
            String bookingDate = "";
            String checkoutDate = "";

            int room = 0;
            int days = 0;

            double roomTotal = 0;
            double serviceTotal = 0;


            String q =
                    "SELECT " +
                    "c.name,c.phone," +
                    "b.room_number," +
                    "r.room_type," +
                    "b.days," +
                    "b.total," +
                    "b.status," +
                    "b.booking_date," +
                    "b.checkout_date," +

                    "COALESCE((" +
                    "SELECT SUM(rs.total) " +
                    "FROM room_services rs " +
                    "WHERE rs.booking_id=" +
                    "b.booking_id" +
                    "),0) " +

                    "FROM bookings b " +

                    "JOIN customers c " +
                    "ON b.customer_id=c.customer_id " +

                    "JOIN rooms r " +
                    "ON b.room_number=r.room_number " +

                    "WHERE b.booking_id=?";


            try (
                    PreparedStatement ps =
                            con.prepareStatement(q)
            ) {

                ps.setInt(
                        1,
                        bookingId
                );


                try (
                        ResultSet rs =
                                ps.executeQuery()
                ) {

                    if (!rs.next()) {

                        sendError(
                                exchange,
                                "Booking not found."
                        );

                        return;
                    }


                    name =
                            rs.getString(1);

                    phone =
                            rs.getString(2);

                    room =
                            rs.getInt(3);

                    roomType =
                            rs.getString(4);

                    days =
                            rs.getInt(5);

                    roomTotal =
                            rs.getDouble(6);

                    status =
                            rs.getString(7);

                    bookingDate =
                            formatDate(
                                    rs.getTimestamp(8)
                            );


                    if (
                            rs.getTimestamp(9)
                                    != null
                    ) {

                        checkoutDate =
                                formatDate(
                                        rs.getTimestamp(9)
                                );
                    }


                    serviceTotal =
                            rs.getDouble(10);
                }
            }


            double grandTotal =
                    roomTotal +
                    serviceTotal;


            StringBuilder serviceRows =
                    new StringBuilder();


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT service_name," +
                                    "quantity,price,total " +
                                    "FROM room_services " +
                                    "WHERE booking_id=? " +
                                    "ORDER BY service_id"
                            )
            ) {

                ps.setInt(
                        1,
                        bookingId
                );


                try (
                        ResultSet rs =
                                ps.executeQuery()
                ) {

                    while (rs.next()) {

                        serviceRows.append(
                                "<tr>"
                        );

                        serviceRows.append(
                                "<td>"
                        );

                        serviceRows.append(
                                esc(
                                        rs.getString(
                                                1
                                        )
                                )
                        );

                        serviceRows.append(
                                "</td>"
                        );

                        serviceRows.append(
                                "<td>"
                        );

                        serviceRows.append(
                                rs.getInt(2)
                        );

                        serviceRows.append(
                                "</td>"
                        );

                        serviceRows.append(
                                "<td>INR "
                        );

                        serviceRows.append(
                                String.format(
                                        Locale.US,
                                        "%.2f",
                                        rs.getDouble(3)
                                )
                        );

                        serviceRows.append(
                                "</td>"
                        );

                        serviceRows.append(
                                "<td>INR "
                        );

                        serviceRows.append(
                                String.format(
                                        Locale.US,
                                        "%.2f",
                                        rs.getDouble(4)
                                )
                        );

                        serviceRows.append(
                                "</td>"
                        );

                        serviceRows.append(
                                "</tr>"
                        );
                    }
                }
            }


            double nightlyRate =
                    days == 0
                            ? 0
                            : roomTotal / days;


            String html =
                    "<!DOCTYPE html>" +

                    "<html>" +

                    "<head>" +

                    "<meta charset='UTF-8'>" +

                    "<title>Invoice #" +
                    bookingId +
                    "</title>" +

                    "<link rel='stylesheet' " +
                    "href='/css/style.css'>" +

                    "</head>" +

                    "<body class='invoice-page'>" +

                    "<div class='invoice-card'>" +

                    "<div class='invoice-head'>" +

                    "<div>" +

                    "<div class='invoice-logo'>GS</div>" +

                    "<h1>Grand Stay Hotel</h1>" +

                    "<p>Guest Invoice</p>" +

                    "</div>" +

                    "<div class='invoice-id'>" +

                    "Invoice #" +
                    bookingId +

                    "</div>" +

                    "</div>" +


                    "<div class='invoice-info'>" +

                    "<div>" +

                    "<b>Guest</b><br>" +

                    esc(name) +

                    "<br>" +

                    esc(phone) +

                    "</div>" +


                    "<div>" +

                    "<b>Room</b><br>" +

                    room +

                    " · " +

                    esc(roomType) +

                    "<br>" +

                    days +

                    " night(s)" +

                    "</div>" +


                    "<div>" +

                    "<b>Status</b><br>" +

                    esc(status) +

                    "<br>" +

                    esc(
                            checkoutDate.isBlank()
                                    ? bookingDate
                                    : checkoutDate
                    ) +

                    "</div>" +

                    "</div>" +


                    "<table class='invoice-table'>" +

                    "<thead>" +

                    "<tr>" +

                    "<th>Item</th>" +

                    "<th>Qty</th>" +

                    "<th>Price</th>" +

                    "<th>Total</th>" +

                    "</tr>" +

                    "</thead>" +

                    "<tbody>" +

                    "<tr>" +

                    "<td>Room stay</td>" +

                    "<td>" +
                    days +
                    "</td>" +

                    "<td>INR " +

                    String.format(
                            Locale.US,
                            "%.2f",
                            nightlyRate
                    ) +

                    "</td>" +

                    "<td>INR " +

                    String.format(
                            Locale.US,
                            "%.2f",
                            roomTotal
                    ) +

                    "</td>" +

                    "</tr>" +

                    serviceRows +

                    "</tbody>" +

                    "</table>" +


                    "<div class='invoice-total'>" +

                    "<span>Room Total</span>" +

                    "<b>INR " +

                    String.format(
                            Locale.US,
                            "%.2f",
                            roomTotal
                    ) +

                    "</b>" +


                    "<span>Services</span>" +

                    "<b>INR " +

                    String.format(
                            Locale.US,
                            "%.2f",
                            serviceTotal
                    ) +

                    "</b>" +


                    "<span>Grand Total</span>" +

                    "<strong>INR " +

                    String.format(
                            Locale.US,
                            "%.2f",
                            grandTotal
                    ) +

                    "</strong>" +

                    "</div>" +


                    "<div class='invoice-actions'>" +

                    "<button " +
                    "class='btn primary' " +
                    "onclick='window.print()'>" +

                    "Print" +

                    "</button>" +


                    "<a class='btn secondary' " +
                    "href='/invoice-pdf?booking_id=" +
                    bookingId +
                    "'>" +

                    "Save as PDF" +

                    "</a>" +


                    "<a class='btn secondary' " +
                    "href='/bookings.html'>" +

                    "Back to History" +

                    "</a>" +

                    "</div>" +

                    "</div>" +

                    "</body>" +

                    "</html>";


            sendHtml(
                    exchange,
                    html
            );


        } catch (Exception e) {

            sendError(
                    exchange,
                    e.getMessage()
            );
        }
    }


    // ==================================================
    // PDF
    // ==================================================

    private static void invoicePdf(
            HttpExchange exchange)
            throws IOException {


        Map<String, String> query =
                parseForm(
                        exchange.getRequestURI()
                                .getRawQuery()
                );


        int bookingId =
                intValue(
                        query.get("booking_id"),
                        0
                );


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            String name = "";
            String status = "";

            int room = 0;
            int days = 0;

            double roomTotal = 0;
            double serviceTotal = 0;


            String q =
                    "SELECT " +

                    "c.name," +
                    "b.room_number," +
                    "b.days," +
                    "b.total," +
                    "b.status," +

                    "COALESCE((" +
                    "SELECT SUM(rs.total) " +
                    "FROM room_services rs " +
                    "WHERE rs.booking_id=b.booking_id" +
                    "),0) " +

                    "FROM bookings b " +

                    "JOIN customers c " +
                    "ON b.customer_id=c.customer_id " +

                    "WHERE b.booking_id=?";


            try (
                    PreparedStatement ps =
                            con.prepareStatement(q)
            ) {

                ps.setInt(
                        1,
                        bookingId
                );


                try (
                        ResultSet rs =
                                ps.executeQuery()
                ) {

                    if (!rs.next()) {

                        sendError(
                                exchange,
                                "Booking not found."
                        );

                        return;
                    }


                    name =
                            rs.getString(1);

                    room =
                            rs.getInt(2);

                    days =
                            rs.getInt(3);

                    roomTotal =
                            rs.getDouble(4);

                    status =
                            rs.getString(5);

                    serviceTotal =
                            rs.getDouble(6);
                }
            }


            double grand =
                    roomTotal +
                    serviceTotal;


            List<String> lines =
                    Arrays.asList(

                            "GRAND STAY HOTEL",

                            "Guest Invoice #" +
                            bookingId,

                            "Guest: " +
                            name,

                            "Room: " +
                            room,

                            "Nights: " +
                            days,

                            "Status: " +
                            status,

                            "Room total: INR " +
                            String.format(
                                    Locale.US,
                                    "%.2f",
                                    roomTotal
                            ),

                            "Services: INR " +
                            String.format(
                                    Locale.US,
                                    "%.2f",
                                    serviceTotal
                            ),

                            "Grand total: INR " +
                            String.format(
                                    Locale.US,
                                    "%.2f",
                                    grand
                            )
                    );


            byte[] pdf =
                    simplePdf(lines);


            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            "application/pdf"
                    );


            exchange.getResponseHeaders()
                    .set(
                            "Content-Disposition",
                            "attachment; filename=\"invoice-"
                            + bookingId +
                            ".pdf\""
                    );


            exchange.sendResponseHeaders(
                    200,
                    pdf.length
            );


            try (
                    OutputStream out =
                            exchange.getResponseBody()
            ) {

                out.write(pdf);
            }


        } catch (Exception e) {

            sendError(
                    exchange,
                    e.getMessage()
            );
        }
    }


    // ==================================================
    // ADD FLOOR
    // ==================================================

    private static void addFloor(
            HttpExchange exchange)
            throws IOException {


        if (!"POST".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            sendJson(
                    exchange,
                    "{\"success\":false," +
                    "\"message\":\"Invalid request.\"}"
            );

            return;
        }


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            int floor =
                    scalarInt(
                            con,
                            "SELECT COALESCE(" +
                            "MAX(floor_number),0)+1 " +
                            "FROM rooms"
                    );


            con.setAutoCommit(false);


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "INSERT INTO rooms " +
                                    "(room_number,floor_number," +
                                    "room_type,price,available)" +
                                    "VALUES(?,?,?,?,TRUE)"
                            )
            ) {


                int[] rooms = {

                        floor * 100 + 1,
                        floor * 100 + 2,
                        floor * 100 + 3
                };


                String[] types = {

                        "Single",
                        "Single",
                        "Deluxe"
                };


                double[] prices = {

                        1500,
                        1500,
                        4000
                };


                for (
                        int i = 0;
                        i < 3;
                        i++
                ) {

                    ps.setInt(
                            1,
                            rooms[i]
                    );

                    ps.setInt(
                            2,
                            floor
                    );

                    ps.setString(
                            3,
                            types[i]
                    );

                    ps.setDouble(
                            4,
                            prices[i]
                    );

                    ps.addBatch();
                }


                ps.executeBatch();
            }


            con.commit();


            sendJson(
                    exchange,
                    "{\"success\":true," +
                    "\"floor\":" +
                    floor +
                    "}"
            );


        } catch (Exception e) {

            sendJson(
                    exchange,
                    "{\"success\":false," +
                    "\"message\":\"" +
                    jsonEscape(
                            e.getMessage()
                    ) +
                    "\"}"
            );
        }
    }


    // ==================================================
    // DELETE FLOOR
    // ==================================================

    private static void deleteFloor(
            HttpExchange exchange)
            throws IOException {


        if (!"POST".equalsIgnoreCase(
                exchange.getRequestMethod())) {

            sendJson(
                    exchange,
                    "{\"success\":false," +
                    "\"message\":\"Invalid request.\"}"
            );

            return;
        }


        Map<String, String> data =
                parseForm(
                        readBody(exchange)
                );


        int floor =
                intValue(
                        data.get("floor"),
                        0
                );


        if (floor <= 0) {

            sendJson(
                    exchange,
                    "{\"success\":false," +
                    "\"message\":\"Invalid floor.\"}"
            );

            return;
        }


        try (
                Connection con =
                        DatabaseConnection.getConnection()
        ) {


            int floors =
                    scalarInt(
                            con,
                            "SELECT COUNT(DISTINCT " +
                            "floor_number) FROM rooms"
                    );


            if (floors <= 1) {

                sendJson(
                        exchange,
                        "{\"success\":false," +
                        "\"message\":\"At least one floor must remain.\"}"
                );

                return;
            }


            int active =
                    scalarInt(
                            con,
                            "SELECT COUNT(*) " +
                            "FROM bookings b " +
                            "JOIN rooms r " +
                            "ON b.room_number=r.room_number " +
                            "WHERE r.floor_number=" +
                            floor +
                            " AND b.status='ACTIVE'"
                    );


            if (active > 0) {

                sendJson(
                        exchange,
                        "{\"success\":false," +
                        "\"message\":\"Cannot delete a floor with active bookings.\"}"
                );

                return;
            }


            con.setAutoCommit(false);


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "DELETE rs FROM " +
                                    "room_services rs " +
                                    "JOIN bookings b " +
                                    "ON rs.booking_id=b.booking_id " +
                                    "JOIN rooms r " +
                                    "ON b.room_number=r.room_number " +
                                    "WHERE r.floor_number=?"
                            )
            ) {

                ps.setInt(
                        1,
                        floor
                );

                ps.executeUpdate();
            }


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "DELETE b FROM bookings b " +
                                    "JOIN rooms r " +
                                    "ON b.room_number=r.room_number " +
                                    "WHERE r.floor_number=?"
                            )
            ) {

                ps.setInt(
                        1,
                        floor
                );

                ps.executeUpdate();
            }


            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "DELETE FROM rooms " +
                                    "WHERE floor_number=?"
                            )
            ) {

                ps.setInt(
                        1,
                        floor
                );

                ps.executeUpdate();
            }


            con.commit();


            sendJson(
                    exchange,
                    "{\"success\":true}"
            );


        } catch (Exception e) {

            sendJson(
                    exchange,
                    "{\"success\":false," +
                    "\"message\":\"" +
                    jsonEscape(
                            e.getMessage()
                    ) +
                    "\"}"
            );
        }
    }


    // ==================================================
    // HELPERS
    // ==================================================

    private static int scalarInt(
            Connection con,
            String sql)
            throws SQLException {


        try (
                PreparedStatement ps =
                        con.prepareStatement(sql);

                ResultSet rs =
                        ps.executeQuery()
        ) {

            if (rs.next()) {

                return rs.getInt(1);
            }


            return 0;
        }
    }


    private static double scalarDouble(
            Connection con,
            String sql)
            throws SQLException {


        try (
                PreparedStatement ps =
                        con.prepareStatement(sql);

                ResultSet rs =
                        ps.executeQuery()
        ) {

            if (rs.next()) {

                return rs.getDouble(1);
            }


            return 0.0;
        }
    }


    private static int intValue(
            String value,
            int defaultValue) {

        try {

            return Integer.parseInt(
                    value == null
                            ? ""
                            : value
            );

        } catch (Exception e) {

            return defaultValue;
        }
    }


    private static String formatDate(
            Timestamp timestamp) {

        if (timestamp == null) {

            return "";
        }


        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.ENGLISH
        ).format(timestamp);
    }


    private static String esc(
            String value) {

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


    private static String jsonEscape(
            String value) {

        if (value == null) {

            return "";
        }


        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
    }


    private static Map<String, String> parseForm(
            String body) {

        Map<String, String> map =
                new HashMap<>();


        if (
                body == null
                        ||
                body.isBlank()
        ) {

            return map;
        }


        for (
                String item :
                body.split("&")
        ) {

            String[] pair =
                    item.split(
                            "=",
                            2
                    );


            String key =
                    urlDecode(
                            pair[0]
                    );


            String value =
                    pair.length > 1
                            ? urlDecode(pair[1])
                            : "";


            map.put(
                    key,
                    value
            );
        }


        return map;
    }


    private static String urlDecode(
            String value) {

        try {

            return URLDecoder.decode(
                    value,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            return value;
        }
    }


    private static String urlEncode(
            String value) {

        try {

            return java.net.URLEncoder.encode(
                    value,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            return value;
        }
    }


    private static String readBody(
            HttpExchange exchange)
            throws IOException {

        return new String(
                exchange
                        .getRequestBody()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );
    }


    private static void redirect(
            HttpExchange exchange,
            String location)
            throws IOException {

        exchange.getResponseHeaders()
                .set(
                        "Location",
                        location
                );

        exchange.sendResponseHeaders(
                302,
                -1
        );

        exchange.close();
    }


    private static void sendJson(
            HttpExchange exchange,
            String json)
            throws IOException {

        byte[] bytes =
                json.getBytes(
                        StandardCharsets.UTF_8
                );


        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );


        exchange.sendResponseHeaders(
                200,
                bytes.length
        );


        try (
                OutputStream out =
                        exchange.getResponseBody()
        ) {

            out.write(bytes);
        }
    }


    private static void sendHtml(
            HttpExchange exchange,
            String html)
            throws IOException {

        byte[] bytes =
                html.getBytes(
                        StandardCharsets.UTF_8
                );


        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/html; charset=UTF-8"
                );


        exchange.sendResponseHeaders(
                200,
                bytes.length
        );


        try (
                OutputStream out =
                        exchange.getResponseBody()
        ) {

            out.write(bytes);
        }
    }


    private static void sendError(
            HttpExchange exchange,
            String message)
            throws IOException {

        sendHtml(
                exchange,
                messagePage(
                        "Request Failed",
                        message == null
                                ? "Something went wrong."
                                : message,
                        "/bookings.html",
                        "Back"
                )
        );
    }


    private static String messagePage(
            String title,
            String message,
            String link,
            String linkText) {

        return
                "<!DOCTYPE html>" +

                "<html>" +

                "<head>" +

                "<meta charset='UTF-8'>" +

                "<title>" +
                esc(title) +
                "</title>" +

                "<link rel='stylesheet' " +
                "href='/css/style.css'>" +

                "</head>" +

                "<body>" +

                "<div class='message-page'>" +

                "<div class='message-card'>" +

                "<div class='message-icon'>!</div>" +

                "<h1>" +
                esc(title) +
                "</h1>" +

                "<p>" +
                esc(message) +
                "</p>" +

                "<a class='btn primary' " +
                "href='" +
                link +
                "'>" +

                esc(linkText) +

                "</a>" +

                "</div>" +

                "</div>" +

                "</body>" +

                "</html>";
    }


    private static void serveFile(
            HttpExchange exchange,
            String file)
            throws IOException {


        Path path =
                WEB.resolve(file)
                        .normalize();


        if (
                !path.startsWith(WEB)
                        ||
                !Files.exists(path)
        ) {

            exchange.sendResponseHeaders(
                    404,
                    -1
            );

            return;
        }


        String contentType =
                "application/octet-stream";


        if (file.endsWith(".html")) {

            contentType =
                    "text/html";

        } else if (file.endsWith(".css")) {

            contentType =
                    "text/css";
        }


        byte[] bytes =
                Files.readAllBytes(path);


        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        contentType +
                        "; charset=UTF-8"
                );


        exchange.sendResponseHeaders(
                200,
                bytes.length
        );


        try (
                OutputStream out =
                        exchange.getResponseBody()
        ) {

            out.write(bytes);
        }
    }


    // ==================================================
    // SIMPLE PDF GENERATOR
    // ==================================================

    private static byte[] simplePdf(
            List<String> lines)
            throws IOException {


        List<String> clean =
                new ArrayList<>();


        for (
                String line :
                lines
        ) {

            clean.add(
                    line
                            .replace(
                                    "\\",
                                    "\\\\"
                            )
                            .replace(
                                    "(",
                                    "\\("
                            )
                            .replace(
                                    ")",
                                    "\\)"
                            )
            );
        }


        StringBuilder stream =
                new StringBuilder();


        stream.append(
                "BT /F1 16 Tf 50 790 Td "
        );


        for (
                int i = 0;
                i < clean.size();
                i++
        ) {

            if (i > 0) {

                stream.append(
                        "0 -28 Td "
                );
            }


            stream.append("(")
                    .append(clean.get(i))
                    .append(") Tj ");
        }


        stream.append(
                "ET"
        );


        String[] objects = {

                "<< /Type /Catalog " +
                "/Pages 2 0 R >>",

                "<< /Type /Pages " +
                "/Kids [3 0 R] " +
                "/Count 1 >>",

                "<< /Type /Page " +
                "/Parent 2 0 R " +
                "/MediaBox [0 0 595 842] " +
                "/Resources << " +
                "/Font << " +
                "/F1 5 0 R >> >> " +
                "/Contents 4 0 R >>",

                "<< /Length " +
                stream.length() +
                " >>\n" +

                "stream\n" +
                stream +
                "\nendstream",

                "<< /Type /Font " +
                "/Subtype /Type1 " +
                "/BaseFont /Helvetica >>"
        };


        ByteArrayOutputStream out =
                new ByteArrayOutputStream();


        out.write(
                "%PDF-1.4\n"
                        .getBytes(
                                StandardCharsets.US_ASCII
                        )
        );


        List<Integer> offsets =
                new ArrayList<>();


        offsets.add(0);


        for (
                int i = 0;
                i < objects.length;
                i++
        ) {

            offsets.add(
                    out.size()
            );


            out.write(
                    (
                            (i + 1) +
                            " 0 obj\n" +
                            objects[i] +
                            "\nendobj\n"
                    ).getBytes(
                            StandardCharsets.US_ASCII
                    )
            );
        }


        int xref =
                out.size();


        out.write(
                (
                        "xref\n" +
                        "0 " +
                        (objects.length + 1) +
                        "\n" +
                        "0000000000 65535 f \n"
                ).getBytes(
                        StandardCharsets.US_ASCII
                )
        );


        for (
                int i = 1;
                i < offsets.size();
                i++
        ) {

            out.write(
                    String.format(
                            Locale.US,
                            "%010d 00000 n \n",
                            offsets.get(i)
                    ).getBytes(
                            StandardCharsets.US_ASCII
                    )
            );
        }


        out.write(
                (
                        "trailer\n" +

                        "<< /Size " +
                        (objects.length + 1) +
                        " /Root 1 0 R >>\n" +

                        "startxref\n" +

                        xref +

                        "\n%%EOF"
                ).getBytes(
                        StandardCharsets.US_ASCII
                )
        );


        return out.toByteArray();
    }
}