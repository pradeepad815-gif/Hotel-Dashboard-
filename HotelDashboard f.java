import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * SMART HOTEL RESERVATION SYSTEM - Multi-page Dashboard with Admin + Festival Offers
 *
 * LOGIN     : Admin    -> admin    / admin123
 *             Customer -> customer / customer123   (or Register a new customer)
 *
 * ADMIN pages    : Dashboard, Manage Rooms (add / edit price / delete), Smart Room Search,
 *                  Offers (add / change % / enable-disable / delete), Bookings (check-out / cancel),
 *                  Customers
 * CUSTOMER pages : Dashboard, View Rooms, Smart Room Search, Offers, My Bookings
 *
 * ADMIN also gets a Reports page (revenue, total discount given, offer performance).
 * OFFERS  : Pooja 15% (POOJA15), Diwali 25% (DIWALI25), Pongal 20% (PONGAL20), New Year 10% (NEWYEAR10,
 *           disabled by default - admin can enable it). Pick from the dropdown or type the promo code.
 *           Offers are applied while
 *           searching / booking. A discount lowers the price per night, so the same budget
 *           lets the guest stay MORE DAYS.
 *
 * Compile : javac HotelDashboard.java
 * Run     : java HotelDashboard
 */
public class HotelDashboard extends JFrame {

    // ================= COLOUR PALETTE =================
    static final Color NAVY      = new Color(15, 48, 85);
    static final Color NAVY_DARK = new Color(10, 25, 47);
    static final Color SIDEBAR   = new Color(20, 33, 61);
    static final Color HOVER     = new Color(42, 64, 108);
    static final Color ORANGE    = new Color(255, 159, 28);
    static final Color BG        = new Color(238, 243, 250);
    static final String FONT     = "Segoe UI";

    // ================= DATA MODEL =================
    static class User {
        String username, password, fullName, role;      // role = "Admin" or "Customer"
        User(String u, String p, String n, String r) { username = u; password = p; fullName = n; role = r; }
    }

    static class Room {
        int no, capacity;
        String type, facilities;
        double price;                // per night
        boolean booked;
        Room(int no, String type, int capacity, double price, String facilities) {
            this.no = no; this.type = type; this.capacity = capacity;
            this.price = price; this.facilities = facilities;
        }
    }

    static class Offer {
        String name, code, description;
        int percent;
        boolean active;
        Offer(String name, String code, int percent, String description, boolean active) {
            this.name = name; this.code = code; this.percent = percent;
            this.description = description; this.active = active;
        }
    }

    /** One suggestion = a single room or a combination of rooms */
    static class Option {
        List<Room> rooms = new ArrayList<>();
        int capacity() { int c = 0; for (Room r : rooms) c += r.capacity; return c; }
        double price() { double p = 0; for (Room r : rooms) p += r.price; return p; }
        double offerPrice(int pct) { return price() * (100 - pct) / 100.0; }
        String nos()   { StringBuilder s = new StringBuilder(); for (Room r : rooms) s.append(s.length() == 0 ? "" : " + ").append(r.no); return s.toString(); }
        String types() { StringBuilder s = new StringBuilder(); for (Room r : rooms) s.append(s.length() == 0 ? "" : " + ").append(r.type); return s.toString(); }
        /** BUDGET -> DAYS : total budget / (price per night after offer) */
        int days(double budget, int pct) { return (int) Math.floor(budget / offerPrice(pct) + 1e-9); }
    }

    static class Booking {
        String id, customer, owner, status, offerName;
        List<Room> rooms;
        int guests, nights;
        double original, discount, total;
        String roomNos() { StringBuilder s = new StringBuilder(); for (Room r : rooms) s.append(s.length() == 0 ? "" : ", ").append(r.no); return s.toString(); }
    }

    final Map<String, User> users = new LinkedHashMap<>();
    final List<Room> rooms = new ArrayList<>();
    final List<Offer> offers = new ArrayList<>();
    final List<Booking> bookings = new ArrayList<>();
    int bookingSeq = 1000;
    User current;

    // ================= UI STATE =================
    final CardLayout rootLayout = new CardLayout();
    final JPanel rootPanel = new JPanel(rootLayout);
    JPanel appPanel;
    CardLayout cardLayout = new CardLayout();
    JPanel pages = new JPanel(cardLayout);
    final Map<String, SideButton> navButtons = new LinkedHashMap<>();
    SideButton activeBtn;

    JLabel lblTotal, lblAvail, lblBooked, lblRes, bannerLabel, offerHint, lblRevenue, lblDiscount, lblTopOffer;
    JPanel barsPanel;
    JComboBox<String> offerBox;
    DefaultTableModel recentModel, roomModel, bookingModel, resultModel, offerModel, customerModel, reportModel;
    final List<Booking> shown = new ArrayList<>();        // bookings visible to current user
    final List<Offer> boxOffers = new ArrayList<>();      // offers in the search combo (index + 1)

    final List<Option> options = new ArrayList<>();
    double lastBudget; int lastGuests, lastWantedDays;
    Offer lastOffer;

    boolean isAdmin() { return current != null && current.role.equals("Admin"); }
    static String money(double d) { return String.format("%.0f", d); }

    public HotelDashboard() {
        seedData();
        setTitle("Smart Hotel Reservation System");
        setSize(1250, 780);
        setMinimumSize(new Dimension(1100, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        rootPanel.add(buildLogin(), "login");
        add(rootPanel);
        rootLayout.show(rootPanel, "login");
        setVisible(true);
    }

    // ================= SAMPLE DATA =================
    void seedData() {
        users.put("admin",    new User("admin", "admin123", "Administrator", "Admin"));
        users.put("customer", new User("customer", "customer123", "Demo Customer", "Customer"));
        users.put("kavya",    new User("kavya", "kavya123", "Kavya", "Customer"));
        users.put("ramesh",   new User("ramesh", "ramesh123", "Ramesh", "Customer"));

        offers.add(new Offer("Pooja Offer", "POOJA15", 15, "Special pooja season discount on all rooms", true));
        offers.add(new Offer("Diwali Offer", "DIWALI25", 25, "Festival of lights mega discount", true));
        offers.add(new Offer("Pongal Offer", "PONGAL20", 20, "Thai Pongal harvest festival discount", true));
        offers.add(new Offer("New Year Offer", "NEWYEAR10", 10, "New year celebration discount", false));

        rooms.add(new Room(101, "Single", 1, 1200, "WiFi"));
        rooms.add(new Room(102, "Double", 2, 2000, "AC, WiFi"));
        rooms.add(new Room(103, "Double", 2, 2200, "AC, WiFi, Breakfast"));
        rooms.add(new Room(104, "Family", 4, 3800, "AC, WiFi, TV"));
        rooms.add(new Room(105, "Family", 5, 4500, "AC, WiFi, Pool"));
        rooms.add(new Room(106, "Deluxe", 3, 5200, "AC, WiFi, Breakfast"));
        rooms.add(new Room(201, "Single", 1, 1500, "AC, WiFi"));
        rooms.add(new Room(202, "Double", 2, 2500, "AC, WiFi, TV"));
        rooms.add(new Room(203, "Double", 2, 2800, "AC, WiFi, Pool"));
        rooms.add(new Room(204, "Family", 4, 4200, "AC, WiFi, Breakfast"));
        rooms.add(new Room(205, "Suite", 6, 8500, "AC, WiFi, Pool, Breakfast"));
        rooms.add(new Room(206, "Deluxe", 3, 5800, "AC, WiFi, Pool"));

        addBooking("Kavya", "kavya", new ArrayList<>(List.of(rooms.get(1))), 2, 3, null);
        addBooking("Ramesh", "ramesh", new ArrayList<>(List.of(rooms.get(8))), 2, 2, offers.get(1));
    }

    Booking addBooking(String customer, String owner, List<Room> rs, int guests, int nights, Offer offer) {
        Booking b = new Booking();
        b.id = "HR" + (++bookingSeq);
        b.customer = customer; b.owner = owner; b.rooms = rs; b.guests = guests; b.nights = nights;
        double perNight = 0;
        for (Room r : rs) { r.booked = true; perNight += r.price; }
        b.original = perNight * nights;
        int pct = offer == null ? 0 : offer.percent;
        b.discount = b.original * pct / 100.0;
        b.total = b.original - b.discount;
        b.offerName = offer == null ? "-" : offer.name;
        b.status = "Confirmed";
        bookings.add(b);
        return b;
    }

    // =====================================================
    //  LOGIN PAGE
    // =====================================================
    JPanel buildLogin() {
        GradientPanel bg = new GradientPanel(NAVY_DARK, new Color(0, 119, 182), 0);
        bg.setLayout(new GridBagLayout());

        RoundedPanel box = new RoundedPanel(Color.WHITE, 26);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(30, 45, 34, 49));

        JLabel t = new JLabel("SMART HOTEL RESERVATION");
        t.setFont(new Font(FONT, Font.BOLD, 24));
        t.setForeground(NAVY);
        t.setAlignmentX(0);
        JLabel s = new JLabel("Sign in to continue");
        s.setFont(new Font(FONT, Font.PLAIN, 14));
        s.setForeground(new Color(100, 110, 130));
        s.setAlignmentX(0);

        JTextField user = new JTextField(22);
        JPasswordField pass = new JPasswordField(22);
        user.setFont(new Font(FONT, Font.PLAIN, 15));
        pass.setFont(new Font(FONT, Font.PLAIN, 15));

        JLabel hint = new JLabel("<html>Admin: admin / admin123<br>Customer: customer / customer123</html>");
        hint.setFont(new Font(FONT, Font.ITALIC, 12));
        hint.setForeground(new Color(110, 120, 140));
        hint.setAlignmentX(0);

        RoundButton login = new RoundButton("Login", new Color(22, 160, 133));
        RoundButton reg = new RoundButton("Register", new Color(41, 128, 185));
        login.setFont(new Font(FONT, Font.BOLD, 15));
        reg.setFont(new Font(FONT, Font.BOLD, 15));
        JPanel btns = new JPanel(new GridLayout(1, 2, 12, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(0);
        btns.setPreferredSize(new Dimension(0, 42));
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btns.add(login); btns.add(reg);

        box.add(t); box.add(s); box.add(Box.createVerticalStrut(20));
        box.add(fieldBlock("Username", user)); box.add(Box.createVerticalStrut(10));
        box.add(fieldBlock("Password", pass)); box.add(Box.createVerticalStrut(10));
        box.add(hint); box.add(Box.createVerticalStrut(18));
        box.add(btns);

        RoundButton qa = new RoundButton("Login as Admin", new Color(142, 68, 173));
        RoundButton qc = new RoundButton("Login as Customer", new Color(230, 126, 34));
        qa.setFont(new Font(FONT, Font.BOLD, 13));
        qc.setFont(new Font(FONT, Font.BOLD, 13));
        qa.addActionListener(e -> loginAs("admin", "admin123"));
        qc.addActionListener(e -> loginAs("customer", "customer123"));
        JPanel quick = new JPanel(new GridLayout(1, 2, 12, 0));
        quick.setOpaque(false);
        quick.setAlignmentX(0);
        quick.setPreferredSize(new Dimension(0, 38));
        quick.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        quick.add(qa); quick.add(qc);
        box.add(Box.createVerticalStrut(10));
        box.add(quick);

        Runnable doLogin = () -> loginAs(user.getText().trim(), new String(pass.getPassword()));
        login.addActionListener(e -> doLogin.run());
        pass.addActionListener(e -> doLogin.run());
        reg.addActionListener(e -> register());

        bg.add(box);
        return bg;
    }

    void loginAs(String username, String password) {
        User u = users.get(username);
        if (u != null && u.password.equals(password)) {
            current = u;
            buildApp();
            rootLayout.show(rootPanel, "app");
            go("Dashboard");
        } else {
            JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    JPanel fieldBlock(String name, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setAlignmentX(0);
        JLabel l = new JLabel(name);
        l.setFont(new Font(FONT, Font.BOLD, 13));
        l.setForeground(NAVY);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        return p;
    }

    void register() {
        JTextField name = new JTextField(), user = new JTextField();
        JPasswordField pass = new JPasswordField();
        JPanel p = new JPanel(new GridLayout(0, 2, 10, 10));
        p.add(new JLabel("Full name:"));  p.add(name);
        p.add(new JLabel("Username:"));   p.add(user);
        p.add(new JLabel("Password:"));   p.add(pass);
        if (JOptionPane.showConfirmDialog(this, p, "Customer Registration", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String u = user.getText().trim();
        if (name.getText().isBlank() || u.isEmpty() || pass.getPassword().length == 0) {
            JOptionPane.showMessageDialog(this, "All fields are required.");
            return;
        }
        if (users.containsKey(u)) {
            JOptionPane.showMessageDialog(this, "Username already exists.");
            return;
        }
        users.put(u, new User(u, new String(pass.getPassword()), name.getText().trim(), "Customer"));
        JOptionPane.showMessageDialog(this, "Registration successful. You can login now.");
    }

    // =====================================================
    //  MAIN APPLICATION SHELL (built after login, depends on role)
    // =====================================================
    void buildApp() {
        if (appPanel != null) rootPanel.remove(appPanel);
        navButtons.clear();
        activeBtn = null;
        options.clear();
        cardLayout = new CardLayout();
        pages = new JPanel(cardLayout);
        pages.setBackground(BG);

        appPanel = new JPanel(new BorderLayout());
        appPanel.setBackground(BG);

        // ---------- header ----------
        GradientPanel header = new GradientPanel(NAVY_DARK, new Color(0, 119, 182), 0);
        header.setLayout(new BorderLayout());
        header.setPreferredSize(new Dimension(1200, 80));
        JLabel title = new JLabel("   SMART HOTEL RESERVATION SYSTEM");
        title.setForeground(Color.WHITE);
        title.setFont(new Font(FONT, Font.BOLD, 25));

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 22));
        rightBox.setOpaque(false);
        JLabel who = new JLabel(current.fullName + "  (" + current.role + ")");
        who.setForeground(Color.WHITE);
        who.setFont(new Font(FONT, Font.BOLD, 15));
        RoundButton logout = new RoundButton("Logout", new Color(231, 76, 60));
        logout.setPreferredSize(new Dimension(95, 34));
        logout.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Do you want to logout?", "Logout", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                current = null;
                rootLayout.show(rootPanel, "login");
            }
        });
        rightBox.add(who); rightBox.add(logout);
        header.add(title, BorderLayout.WEST);
        header.add(rightBox, BorderLayout.EAST);

        // ---------- sidebar (menu depends on role) ----------
        String[] menu = isAdmin()
                ? new String[]{"Dashboard", "Manage Rooms", "Smart Room Search", "Offers", "Bookings", "Customers", "Reports"}
                : new String[]{"Dashboard", "View Rooms", "Smart Room Search", "Offers", "My Bookings"};

        JPanel side = new JPanel(new BorderLayout());
        side.setPreferredSize(new Dimension(220, 665));
        side.setBackground(SIDEBAR);
        JPanel menuPanel = new JPanel(new GridLayout(8, 1, 0, 4));
        menuPanel.setBackground(SIDEBAR);
        menuPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
        for (String item : menu) {
            SideButton b = new SideButton(item);
            navButtons.put(item, b);
            menuPanel.add(b);
            b.addActionListener(e -> go(item));
        }
        side.add(menuPanel, BorderLayout.NORTH);

        // ---------- pages ----------
        pages.add(buildDashboardPage(), "Dashboard");
        pages.add(buildRoomsPage(), isAdmin() ? "Manage Rooms" : "View Rooms");
        pages.add(buildSearchPage(), "Smart Room Search");
        pages.add(buildOffersPage(), "Offers");
        pages.add(buildBookingsPage(), isAdmin() ? "Bookings" : "My Bookings");
        if (isAdmin()) { pages.add(buildCustomersPage(), "Customers"); pages.add(buildReportsPage(), "Reports"); }

        appPanel.add(header, BorderLayout.NORTH);
        appPanel.add(side, BorderLayout.WEST);
        appPanel.add(pages, BorderLayout.CENTER);
        rootPanel.add(appPanel, "app");
    }

    void go(String page) {
        if (activeBtn != null) { activeBtn.active = false; activeBtn.repaint(); }
        activeBtn = navButtons.get(page);
        if (activeBtn != null) { activeBtn.active = true; activeBtn.repaint(); }
        refreshAll();
        cardLayout.show(pages, page);
    }

    // =====================================================
    //  PAGE 1 : DASHBOARD
    // =====================================================
    JPanel buildDashboardPage() {
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(BG);

        JLabel heading = new JLabel(isAdmin() ? "Admin Dashboard" : "Dashboard Overview");
        heading.setFont(new Font(FONT, Font.BOLD, 28));
        heading.setForeground(NAVY);
        heading.setBorder(BorderFactory.createEmptyBorder(18, 25, 8, 10));

        bannerLabel = new JLabel();
        bannerLabel.setFont(new Font(FONT, Font.BOLD, 15));
        bannerLabel.setForeground(Color.WHITE);
        bannerLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        GradientPanel banner = new GradientPanel(new Color(255, 140, 0), new Color(214, 40, 40), 22);
        banner.setLayout(new BorderLayout());
        banner.setPreferredSize(new Dimension(0, 44));
        banner.add(bannerLabel, BorderLayout.CENTER);
        JPanel bannerWrap = new JPanel(new BorderLayout());
        bannerWrap.setOpaque(false);
        bannerWrap.setBorder(BorderFactory.createEmptyBorder(0, 25, 6, 25));
        bannerWrap.add(banner, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(bannerWrap, BorderLayout.CENTER);
        center.add(top, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(15, 15));
        content.setBackground(BG);
        content.setBorder(BorderFactory.createEmptyBorder(5, 20, 20, 20));

        lblTotal = new JLabel(); lblAvail = new JLabel(); lblBooked = new JLabel(); lblRes = new JLabel();
        JPanel cards = new JPanel(new GridLayout(1, 4, 15, 15));
        cards.setOpaque(false);
        cards.setPreferredSize(new Dimension(0, 110));
        cards.add(statCard("TOTAL ROOMS", lblTotal, new Color(52, 152, 219), new Color(41, 98, 255)));
        cards.add(statCard("AVAILABLE ROOMS", lblAvail, new Color(46, 204, 113), new Color(22, 160, 133)));
        cards.add(statCard("BOOKED ROOMS", lblBooked, new Color(255, 159, 28), new Color(231, 76, 60)));
        cards.add(statCard(isAdmin() ? "TOTAL RESERVATIONS" : "MY RESERVATIONS", lblRes, new Color(155, 89, 182), new Color(106, 17, 203)));
        content.add(cards, BorderLayout.NORTH);

        JPanel actions = new JPanel(new GridLayout(1, 4, 15, 15));
        actions.setOpaque(false);
        RoundButton bSearch = action("Find Suitable Room", new Color(22, 160, 133));
        RoundButton bOffers = action("Festival Offers", new Color(214, 40, 40));
        RoundButton bBook   = action(isAdmin() ? "View Bookings" : "My Bookings", new Color(230, 126, 34));
        RoundButton bBill   = action("Generate Bill", new Color(142, 68, 173));
        actions.add(bSearch); actions.add(bOffers); actions.add(bBook); actions.add(bBill);
        content.add(section("Quick Actions", actions), BorderLayout.CENTER);

        bSearch.addActionListener(e -> go("Smart Room Search"));
        bOffers.addActionListener(e -> go("Offers"));
        bBook.addActionListener(e -> go(isAdmin() ? "Bookings" : "My Bookings"));
        bBill.addActionListener(e -> showBill());

        JPanel bottom = new JPanel(new GridLayout(1, 2, 15, 15));
        bottom.setOpaque(false);
        bottom.setPreferredSize(new Dimension(0, 235));
        recentModel = tableModel("Booking ID", "Customer", "Room", "Status");
        JTable recent = new JTable(recentModel);
        styleTable(recent);
        bottom.add(section("Recent Bookings", scroll(recent)));
        barsPanel = new JPanel(new GridLayout(4, 1, 0, 10));
        barsPanel.setOpaque(false);
        bottom.add(section("Room Availability", barsPanel));
        content.add(bottom, BorderLayout.SOUTH);

        center.add(content, BorderLayout.CENTER);
        return center;
    }

    void showBill() {
        Booking b = null;
        for (int i = shown.size() - 1; i >= 0; i--)
            if (!shown.get(i).status.equals("Cancelled")) { b = shown.get(i); break; }
        if (b == null) {
            JOptionPane.showMessageDialog(this, "No booking available to generate a bill.");
            return;
        }
        JOptionPane.showMessageDialog(this, billText(b), "Generate Bill", JOptionPane.INFORMATION_MESSAGE);
    }

    String billText(Booking b) {
        return "BILL - " + b.id + "\n\n" +
                "Customer      : " + b.customer + "\n" +
                "Room(s)       : " + b.roomNos() + "\n" +
                "Nights        : " + b.nights + "\n" +
                "Room Charges  : Rs " + money(b.original) + "\n" +
                "Offer         : " + b.offerName + "\n" +
                "Discount      : - Rs " + money(b.discount) + "\n" +
                "TOTAL PAYABLE : Rs " + money(b.total) + "\n" +
                "Status        : " + b.status;
    }

    // =====================================================
    //  PAGE 2 : MANAGE ROOMS (admin) / VIEW ROOMS (customer)
    // =====================================================
    JPanel buildRoomsPage() {
        roomModel = tableModel("Room No", "Type", "Capacity", "Price / Night (Rs)", "Facilities", "Status");
        JTable t = new JTable(roomModel);
        styleTable(t);

        JComponent body = scroll(t);
        if (isAdmin()) {
            RoundButton add = new RoundButton("Add Room", new Color(39, 174, 96));
            RoundButton edit = new RoundButton("Edit Price", new Color(41, 128, 185));
            RoundButton del = new RoundButton("Delete Room", new Color(231, 76, 60));
            for (RoundButton b : new RoundButton[]{add, edit, del}) { b.setPreferredSize(new Dimension(150, 40)); b.setFont(new Font(FONT, Font.BOLD, 15)); }

            add.addActionListener(e -> addRoomDialog());
            edit.addActionListener(e -> {
                int row = t.getSelectedRow();
                if (row < 0) { JOptionPane.showMessageDialog(this, "Select a room first."); return; }
                Room r = rooms.get(row);
                String in = JOptionPane.showInputDialog(this, "New price per night for room " + r.no + ":", money(r.price));
                if (in == null) return;
                try {
                    double p = Double.parseDouble(in.trim());
                    if (p <= 0) throw new NumberFormatException();
                    r.price = p;
                    refreshAll();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Enter a valid price.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            del.addActionListener(e -> {
                int row = t.getSelectedRow();
                if (row < 0) { JOptionPane.showMessageDialog(this, "Select a room first."); return; }
                Room r = rooms.get(row);
                if (r.booked) { JOptionPane.showMessageDialog(this, "Room " + r.no + " is currently booked and cannot be deleted."); return; }
                if (JOptionPane.showConfirmDialog(this, "Delete room " + r.no + "?", "Delete", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    rooms.remove(row);
                    refreshAll();
                }
            });

            JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            south.setOpaque(false);
            south.add(add); south.add(edit); south.add(del);
            JPanel wrap = new JPanel(new BorderLayout(0, 10));
            wrap.setOpaque(false);
            wrap.add(body, BorderLayout.CENTER);
            wrap.add(south, BorderLayout.SOUTH);
            body = wrap;
        }
        return pageShell(isAdmin() ? "Manage Rooms" : "View Rooms", section("All Rooms", body));
    }

    void addRoomDialog() {
        JTextField no = new JTextField();
        JComboBox<String> type = new JComboBox<>(new String[]{"Single", "Double", "Family", "Deluxe", "Suite"});
        JSpinner cap = new JSpinner(new SpinnerNumberModel(2, 1, 12, 1));
        JTextField price = new JTextField();
        JTextField fac = new JTextField("AC, WiFi");
        JPanel p = new JPanel(new GridLayout(0, 2, 10, 10));
        p.add(new JLabel("Room number:"));        p.add(no);
        p.add(new JLabel("Room type:"));          p.add(type);
        p.add(new JLabel("Capacity (guests):"));  p.add(cap);
        p.add(new JLabel("Price per night (Rs):")); p.add(price);
        p.add(new JLabel("Facilities:"));         p.add(fac);
        if (JOptionPane.showConfirmDialog(this, p, "Add Room", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            int n = Integer.parseInt(no.getText().trim());
            double pr = Double.parseDouble(price.getText().trim());
            if (pr <= 0) throw new NumberFormatException();
            for (Room r : rooms) if (r.no == n) { JOptionPane.showMessageDialog(this, "Room number already exists."); return; }
            rooms.add(new Room(n, (String) type.getSelectedItem(), (Integer) cap.getValue(), pr, fac.getText().trim()));
            rooms.sort(Comparator.comparingInt(r -> r.no));
            refreshAll();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter valid room number and price.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =====================================================
    //  PAGE 3 : SMART ROOM SEARCH (budget -> days, with offers)
    // =====================================================
    JPanel buildSearchPage() {
        JSpinner guests = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        JTextField budget = new JTextField("10000");
        JSpinner days = new JSpinner(new SpinnerNumberModel(0, 0, 60, 1));
        JComboBox<String> type = new JComboBox<>(new String[]{"Any", "Single", "Double", "Family", "Deluxe", "Suite"});
        offerBox = new JComboBox<>();
        for (JComponent c : new JComponent[]{guests, budget, days, type, offerBox}) c.setFont(new Font(FONT, Font.PLAIN, 15));

        RoundButton search = new RoundButton("Search Rooms", new Color(22, 160, 133));
        search.setFont(new Font(FONT, Font.BOLD, 15));

        JTextField promo = new JTextField();
        promo.setFont(new Font(FONT, Font.PLAIN, 15));
        JPanel form = new JPanel(new GridLayout(2, 7, 12, 4));
        form.setOpaque(false);
        form.add(formLabel("Number of Guests"));
        form.add(formLabel("Total Budget (Rs)"));
        form.add(formLabel("Days wanted (0 = any)"));
        form.add(formLabel("Room Type"));
        form.add(formLabel("Festival Offer"));
        form.add(formLabel("Promo Code (optional)"));
        form.add(new JLabel(""));
        form.add(guests); form.add(budget); form.add(days); form.add(type); form.add(offerBox); form.add(promo); form.add(search);
        form.setPreferredSize(new Dimension(0, 70));

        offerHint = new JLabel("Days you can stay = Total Budget \u00F7 Price per night (after offer discount)");
        offerHint.setFont(new Font(FONT, Font.ITALIC, 13));
        offerHint.setForeground(new Color(90, 100, 120));

        JPanel formBox = new JPanel(new BorderLayout(0, 8));
        formBox.setOpaque(false);
        formBox.add(form, BorderLayout.CENTER);
        formBox.add(offerHint, BorderLayout.SOUTH);

        resultModel = tableModel("Option", "Room(s)", "Type", "Capacity", "Price / Night (Rs)",
                "After Offer / Night (Rs)", "Days You Can Stay", "Cost for Those Days (Rs)", "Balance Left (Rs)");
        JTable rt = new JTable(resultModel);
        styleTable(rt);

        RoundButton book = new RoundButton("Book Selected Option", new Color(39, 174, 96));
        book.setPreferredSize(new Dimension(210, 40));
        book.setFont(new Font(FONT, Font.BOLD, 15));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(book);

        JPanel resBody = new JPanel(new BorderLayout(0, 10));
        resBody.setOpaque(false);
        resBody.add(scroll(rt), BorderLayout.CENTER);
        resBody.add(south, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 15));
        body.setOpaque(false);
        body.add(section("Your Requirements", formBox), BorderLayout.NORTH);
        body.add(section("Suggested Rooms", resBody), BorderLayout.CENTER);

        search.addActionListener(e -> {
            try {
                double b = Double.parseDouble(budget.getText().trim());
                if (b <= 0) throw new NumberFormatException();
                int idx = offerBox.getSelectedIndex();
                Offer of = idx > 0 && idx - 1 < boxOffers.size() ? boxOffers.get(idx - 1) : null;
                String code = promo.getText().trim();
                if (!code.isEmpty()) {                       // a typed promo code overrides the dropdown
                    Offer match = null;
                    for (Offer x : offers) if (x.active && x.code.equalsIgnoreCase(code)) match = x;
                    if (match == null) {
                        JOptionPane.showMessageDialog(this, "Invalid or inactive promo code: " + code, "Promo Code", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    of = match;
                }
                doSearch((Integer) guests.getValue(), b, (Integer) days.getValue(), (String) type.getSelectedItem(), of);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter a valid budget amount.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        book.addActionListener(e -> {
            int row = rt.getSelectedRow();
            if (row < 0 || row >= options.size()) {
                JOptionPane.showMessageDialog(this, "Please select a suggested option first.");
                return;
            }
            bookOption(options.get(row));
        });
        return pageShell("Smart Room Search", body);
    }

    JLabel formLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font(FONT, Font.BOLD, 13));
        l.setForeground(NAVY);
        return l;
    }

    void doSearch(int guests, double budget, int wantedDays, String type, Offer offer) {
        lastGuests = guests; lastBudget = budget; lastWantedDays = wantedDays; lastOffer = offer;
        int pct = offer == null ? 0 : offer.percent;

        List<Room> avail = new ArrayList<>();
        for (Room r : rooms)
            if (!r.booked && (type.equals("Any") || r.type.equals(type))) avail.add(r);

        List<Option> found = new ArrayList<>();
        for (Room r : avail)
            if (r.capacity >= guests) { Option o = new Option(); o.rooms.add(r); found.add(o); }
        if (found.isEmpty()) {
            avail.sort(Comparator.comparingDouble(r -> r.price));
            combine(avail, 0, new ArrayList<>(), guests, found);
        }

        int minDays = Math.max(1, wantedDays);
        options.clear();
        for (Option o : found) if (o.days(budget, pct) >= minDays) options.add(o);
        options.sort(Comparator.comparingInt((Option o) -> o.rooms.size()).thenComparingDouble(Option::price));
        while (options.size() > 8) options.remove(options.size() - 1);

        resultModel.setRowCount(0);
        int i = 1;
        for (Option o : options) {
            int d = o.days(budget, pct);
            double perNight = o.offerPrice(pct);
            double cost = d * perNight;
            resultModel.addRow(new Object[]{i++, o.nos(), o.types(), o.capacity(),
                    money(o.price()), money(perNight), d + (d == 1 ? " day" : " days"),
                    money(cost), money(budget - cost)});
        }

        offerHint.setText(offer == null
                ? "No offer applied. Choose Pooja / Diwali offer to stay more days with the same budget."
                : "Offer applied: " + offer.name + " (" + offer.code + ") - " + offer.percent + "% off on room price per night");

        if (options.isEmpty()) {
            String why = found.isEmpty()
                    ? "No available room or combination can accommodate " + guests + " guest(s)."
                    : "Rooms are available, but the budget of Rs " + money(budget)
                      + " cannot cover " + minDays + " night(s).\nIncrease the budget, reduce the days or choose a festival offer.";
            JOptionPane.showMessageDialog(this, why, "No suitable room", JOptionPane.WARNING_MESSAGE);
        }
    }

    void combine(List<Room> avail, int start, List<Room> cur, int guests, List<Option> out) {
        int cap = 0;
        for (Room r : cur) cap += r.capacity;
        if (cap >= guests && !cur.isEmpty()) {
            Option o = new Option();
            o.rooms.addAll(cur);
            out.add(o);
            return;
        }
        if (cur.size() == 3) return;
        for (int i = start; i < avail.size(); i++) {
            cur.add(avail.get(i));
            combine(avail, i + 1, cur, guests, out);
            cur.remove(cur.size() - 1);
        }
    }

    void bookOption(Option o) {
        int pct = lastOffer == null ? 0 : lastOffer.percent;
        int maxDays = o.days(lastBudget, pct);
        int startVal = lastWantedDays > 0 ? Math.min(lastWantedDays, maxDays) : maxDays;

        JTextField name = new JTextField(current.fullName);
        JSpinner nights = new JSpinner(new SpinnerNumberModel(startVal, 1, maxDays, 1));
        JPanel p = new JPanel(new GridLayout(0, 2, 10, 10));
        p.add(new JLabel("Customer name:"));  p.add(name);
        p.add(new JLabel("Room(s):"));        p.add(new JLabel(o.nos() + "  (Rs " + money(o.offerPrice(pct)) + "/night)"));
        p.add(new JLabel("Offer:"));          p.add(new JLabel(lastOffer == null ? "None" : lastOffer.name + " - " + pct + "% off"));
        p.add(new JLabel("Days to stay (max " + maxDays + "):")); p.add(nights);

        if (JOptionPane.showConfirmDialog(this, p, "Confirm Booking", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (name.getText().isBlank()) { JOptionPane.showMessageDialog(this, "Customer name is required."); return; }
        for (Room r : o.rooms)
            if (r.booked) {
                JOptionPane.showMessageDialog(this, "A selected room is no longer available. Please search again.");
                resultModel.setRowCount(0); options.clear();
                return;
            }
        int n = (Integer) nights.getValue();
        Booking b = addBooking(name.getText().trim(), current.username, new ArrayList<>(o.rooms), lastGuests, n, lastOffer);
        JOptionPane.showMessageDialog(this,
                "RESERVATION CONFIRMED\n\nBooking ID : " + b.id + "\n\n" + billText(b)
                        + "\n\nYou saved    : Rs " + money(b.discount)
                        + "\nBudget Left : Rs " + money(lastBudget - b.total),
                "Confirmation", JOptionPane.INFORMATION_MESSAGE);
        resultModel.setRowCount(0); options.clear();
        go(isAdmin() ? "Bookings" : "My Bookings");
    }

    // =====================================================
    //  PAGE 4 : OFFERS
    // =====================================================
    JPanel buildOffersPage() {
        offerModel = tableModel("Offer", "Code", "Discount %", "Status", "Description");
        JTable t = new JTable(offerModel);
        styleTable(t);

        JComponent body = scroll(t);
        if (isAdmin()) {
            RoundButton add = new RoundButton("Add Offer", new Color(39, 174, 96));
            RoundButton pct = new RoundButton("Change Discount", new Color(41, 128, 185));
            RoundButton tog = new RoundButton("Enable / Disable", new Color(230, 126, 34));
            RoundButton del = new RoundButton("Delete Offer", new Color(231, 76, 60));
            for (RoundButton b : new RoundButton[]{add, pct, tog, del}) { b.setPreferredSize(new Dimension(170, 40)); b.setFont(new Font(FONT, Font.BOLD, 15)); }

            add.addActionListener(e -> {
                JTextField name = new JTextField(), code = new JTextField(), desc = new JTextField();
                JSpinner pc = new JSpinner(new SpinnerNumberModel(10, 1, 90, 1));
                JPanel p = new JPanel(new GridLayout(0, 2, 10, 10));
                p.add(new JLabel("Offer name:"));   p.add(name);
                p.add(new JLabel("Code:"));         p.add(code);
                p.add(new JLabel("Discount %:"));   p.add(pc);
                p.add(new JLabel("Description:"));  p.add(desc);
                if (JOptionPane.showConfirmDialog(this, p, "Add Offer", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
                if (name.getText().isBlank() || code.getText().isBlank()) { JOptionPane.showMessageDialog(this, "Name and code are required."); return; }
                offers.add(new Offer(name.getText().trim(), code.getText().trim().toUpperCase(), (Integer) pc.getValue(), desc.getText().trim(), true));
                refreshAll();
            });
            pct.addActionListener(e -> {
                int row = t.getSelectedRow();
                if (row < 0) { JOptionPane.showMessageDialog(this, "Select an offer first."); return; }
                Offer o = offers.get(row);
                String in = JOptionPane.showInputDialog(this, "New discount % for " + o.name + ":", o.percent);
                if (in == null) return;
                try {
                    int v = Integer.parseInt(in.trim());
                    if (v < 1 || v > 90) throw new NumberFormatException();
                    o.percent = v;
                    refreshAll();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Enter a whole number between 1 and 90.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            tog.addActionListener(e -> {
                int row = t.getSelectedRow();
                if (row < 0) { JOptionPane.showMessageDialog(this, "Select an offer first."); return; }
                offers.get(row).active = !offers.get(row).active;
                refreshAll();
            });
            del.addActionListener(e -> {
                int row = t.getSelectedRow();
                if (row < 0) { JOptionPane.showMessageDialog(this, "Select an offer first."); return; }
                if (JOptionPane.showConfirmDialog(this, "Delete " + offers.get(row).name + "?", "Delete", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    offers.remove(row);
                    refreshAll();
                }
            });

            JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            south.setOpaque(false);
            south.add(add); south.add(pct); south.add(tog); south.add(del);
            JPanel wrap = new JPanel(new BorderLayout(0, 10));
            wrap.setOpaque(false);
            wrap.add(body, BorderLayout.CENTER);
            wrap.add(south, BorderLayout.SOUTH);
            body = wrap;
        }
        return pageShell("Festival Offers", section(isAdmin() ? "Manage Offers" : "Current Offers - choose one in Smart Room Search", body));
    }

    // =====================================================
    //  PAGE 5 : BOOKINGS (admin = all, customer = own)
    // =====================================================
    JPanel buildBookingsPage() {
        bookingModel = tableModel("Booking ID", "Customer", "Room(s)", "Guests", "Days", "Offer", "Total (Rs)", "Status");
        JTable t = new JTable(bookingModel);
        styleTable(t);

        RoundButton cancel = new RoundButton("Cancel Booking", new Color(231, 76, 60));
        cancel.setPreferredSize(new Dimension(180, 40));
        cancel.setFont(new Font(FONT, Font.BOLD, 15));
        cancel.addActionListener(e -> {
            Booking b = selectedBooking(t);
            if (b == null) return;
            if (!b.status.equals("Confirmed")) { JOptionPane.showMessageDialog(this, "This booking is already closed."); return; }
            if (JOptionPane.showConfirmDialog(this, "Cancel booking " + b.id + "?", "Cancel", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            b.status = "Cancelled";
            for (Room r : b.rooms) r.booked = false;
            refreshAll();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        south.setOpaque(false);
        if (isAdmin()) {
            RoundButton out = new RoundButton("Check-out Selected", new Color(230, 126, 34));
            out.setPreferredSize(new Dimension(190, 40));
            out.setFont(new Font(FONT, Font.BOLD, 15));
            out.addActionListener(e -> {
                Booking b = selectedBooking(t);
                if (b == null) return;
                if (!b.status.equals("Confirmed")) { JOptionPane.showMessageDialog(this, "This booking is already closed."); return; }
                b.status = "Checked-out";
                for (Room r : b.rooms) r.booked = false;
                refreshAll();
            });
            south.add(out);
        }
        south.add(cancel);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(scroll(t), BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        return pageShell(isAdmin() ? "Bookings" : "My Bookings", section(isAdmin() ? "All Reservations" : "Your Reservations", body));
    }

    Booking selectedBooking(JTable t) {
        int row = t.getSelectedRow();
        if (row < 0 || row >= shown.size()) { JOptionPane.showMessageDialog(this, "Select a booking first."); return null; }
        return shown.get(row);
    }

    // =====================================================
    //  PAGE 6 : CUSTOMERS (admin only)
    // =====================================================
    // =====================================================
    //  PAGE 7 : REPORTS (admin only) - revenue, discounts, offer performance
    // =====================================================
    JPanel buildReportsPage() {
        lblRevenue = new JLabel(); lblDiscount = new JLabel(); lblTopOffer = new JLabel();
        JPanel cards = new JPanel(new GridLayout(1, 3, 15, 15));
        cards.setOpaque(false);
        cards.setPreferredSize(new Dimension(0, 110));
        cards.add(statCard("TOTAL REVENUE (Rs)", lblRevenue, new Color(46, 204, 113), new Color(22, 160, 133)));
        cards.add(statCard("DISCOUNT GIVEN (Rs)", lblDiscount, new Color(255, 159, 28), new Color(231, 76, 60)));
        cards.add(statCard("MOST USED OFFER", lblTopOffer, new Color(155, 89, 182), new Color(106, 17, 203)));
        lblTopOffer.setFont(new Font(FONT, Font.BOLD, 26));

        reportModel = tableModel("Offer", "Bookings", "Discount Given (Rs)", "Revenue (Rs)");
        JTable t = new JTable(reportModel);
        styleTable(t);

        JPanel body = new JPanel(new BorderLayout(0, 15));
        body.setOpaque(false);
        body.add(cards, BorderLayout.NORTH);
        body.add(section("Offer Performance (cancelled bookings excluded)", scroll(t)), BorderLayout.CENTER);
        return pageShell("Reports", body);
    }

    JPanel buildCustomersPage() {
        customerModel = tableModel("Username", "Name", "Bookings", "Total Spent (Rs)");
        JTable t = new JTable(customerModel);
        styleTable(t);
        return pageShell("Customers", section("Registered Customers", scroll(t)));
    }

    // =====================================================
    //  REFRESH (keeps every page in sync)
    // =====================================================
    void refreshAll() {
        if (current == null || lblTotal == null) return;

        shown.clear();
        for (Booking b : bookings)
            if (isAdmin() || b.owner.equals(current.username)) shown.add(b);

        int total = rooms.size(), booked = 0;
        for (Room r : rooms) if (r.booked) booked++;
        int avail = total - booked;
        lblTotal.setText(String.valueOf(total));
        lblAvail.setText(String.valueOf(avail));
        lblBooked.setText(String.valueOf(booked));
        lblRes.setText(String.valueOf(shown.size()));

        // festival banner
        StringBuilder sb = new StringBuilder("  FESTIVE OFFERS:  ");
        int cnt = 0;
        for (Offer o : offers) if (o.active) { sb.append(cnt++ > 0 ? "   |   " : "").append(o.name).append(" ").append(o.percent).append("% OFF (").append(o.code).append(")"); }
        if (cnt == 0) sb.append("No offers running right now");
        bannerLabel.setText(sb.toString());

        // availability bars
        int availPct = total == 0 ? 0 : avail * 100 / total;
        int bookedPct = total == 0 ? 0 : booked * 100 / total;
        barsPanel.removeAll();
        barsPanel.add(barRow("Total Rooms", String.valueOf(total), 100, new Color(52, 152, 219)));
        barsPanel.add(barRow("Available Rooms", String.valueOf(avail), availPct, new Color(46, 204, 113)));
        barsPanel.add(barRow("Booked Rooms", String.valueOf(booked), bookedPct, new Color(255, 159, 28)));
        barsPanel.add(barRow("Occupancy Rate", bookedPct + "%", bookedPct, new Color(155, 89, 182)));
        barsPanel.revalidate(); barsPanel.repaint();

        recentModel.setRowCount(0);
        for (int i = shown.size() - 1; i >= 0 && recentModel.getRowCount() < 5; i--) {
            Booking b = shown.get(i);
            recentModel.addRow(new Object[]{b.id, b.customer, b.roomNos(), b.status});
        }

        roomModel.setRowCount(0);
        for (Room r : rooms)
            roomModel.addRow(new Object[]{r.no, r.type, r.capacity, money(r.price), r.facilities, r.booked ? "Booked" : "Available"});

        offerModel.setRowCount(0);
        for (Offer o : offers)
            offerModel.addRow(new Object[]{o.name, o.code, o.percent + "%", o.active ? "Active" : "Disabled", o.description});

        bookingModel.setRowCount(0);
        for (Booking b : shown)
            bookingModel.addRow(new Object[]{b.id, b.customer, b.roomNos(), b.guests, b.nights, b.offerName, money(b.total), b.status});

        if (customerModel != null) {
            customerModel.setRowCount(0);
            for (User u : users.values()) {
                if (!u.role.equals("Customer")) continue;
                int n = 0; double spent = 0;
                for (Booking b : bookings)
                    if (b.owner.equals(u.username) && !b.status.equals("Cancelled")) { n++; spent += b.total; }
                customerModel.addRow(new Object[]{u.username, u.fullName, n, money(spent)});
            }
        }

        if (reportModel != null) {
            Map<String, double[]> perOffer = new LinkedHashMap<>();   // {count, discount, revenue}
            perOffer.put("No Offer", new double[3]);
            for (Offer o : offers) perOffer.put(o.name, new double[3]);
            double revenue = 0, discount = 0;
            for (Booking b : bookings) {
                if (b.status.equals("Cancelled")) continue;
                revenue += b.total; discount += b.discount;
                double[] a = perOffer.computeIfAbsent(b.offerName.equals("-") ? "No Offer" : b.offerName, k -> new double[3]);
                a[0]++; a[1] += b.discount; a[2] += b.total;
            }
            reportModel.setRowCount(0);
            String top = "None"; double topCount = 0;
            for (Map.Entry<String, double[]> en : perOffer.entrySet()) {
                double[] a = en.getValue();
                reportModel.addRow(new Object[]{en.getKey(), (int) a[0], money(a[1]), money(a[2])});
                if (!en.getKey().equals("No Offer") && a[0] > topCount) { topCount = a[0]; top = en.getKey(); }
            }
            lblRevenue.setText(money(revenue));
            lblDiscount.setText(money(discount));
            lblTopOffer.setText(top);
        }

        // refresh offer combo in the search page (keep selection if possible)
        Offer previous = null;
        int sel = offerBox.getSelectedIndex();
        if (sel > 0 && sel - 1 < boxOffers.size()) previous = boxOffers.get(sel - 1);
        boxOffers.clear();
        offerBox.removeAllItems();
        offerBox.addItem("No Offer");
        for (Offer o : offers) if (o.active) { boxOffers.add(o); offerBox.addItem(o.name + " (" + o.percent + "% off)"); }
        if (previous != null && boxOffers.contains(previous)) offerBox.setSelectedIndex(boxOffers.indexOf(previous) + 1);
    }

    // =====================================================
    //  UI HELPERS
    // =====================================================
    JPanel pageShell(String headingText, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        JLabel h = new JLabel(headingText);
        h.setFont(new Font(FONT, Font.BOLD, 28));
        h.setForeground(NAVY);
        h.setBorder(BorderFactory.createEmptyBorder(22, 25, 10, 10));
        p.add(h, BorderLayout.NORTH);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG);
        wrap.setBorder(BorderFactory.createEmptyBorder(5, 20, 20, 20));
        wrap.add(body, BorderLayout.CENTER);
        p.add(wrap, BorderLayout.CENTER);
        return p;
    }

    DefaultTableModel tableModel(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    JScrollPane scroll(JTable t) {
        JScrollPane s = new JScrollPane(t);
        s.setBorder(null);
        s.getViewport().setBackground(Color.WHITE);
        return s;
    }

    private JPanel statCard(String name, JLabel number, Color c1, Color c2) {
        GradientPanel panel = new GradientPanel(c1, c2, 22);
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 18, 15, 18));
        JLabel t = new JLabel(name);
        t.setFont(new Font(FONT, Font.BOLD, 13));
        t.setForeground(new Color(255, 255, 255, 220));
        number.setFont(new Font(FONT, Font.BOLD, 38));
        number.setForeground(Color.WHITE);
        panel.add(t, BorderLayout.NORTH);
        panel.add(number, BorderLayout.CENTER);
        return panel;
    }

    private RoundButton action(String text, Color color) {
        RoundButton b = new RoundButton(text, color);
        b.setFont(new Font(FONT, Font.BOLD, 15));
        return b;
    }

    private JPanel section(String titleText, JComponent body) {
        RoundedPanel p = new RoundedPanel(Color.WHITE, 22);
        p.setLayout(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 16, 18));
        JLabel l = new JLabel(titleText);
        l.setFont(new Font(FONT, Font.BOLD, 18));
        l.setForeground(NAVY);
        p.add(l, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel barRow(String label, String value, int percent, Color color) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(new Font(FONT, Font.BOLD, 14));
        l.setForeground(new Color(50, 60, 80));
        l.setPreferredSize(new Dimension(130, 20));

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(percent);
        bar.setForeground(color);
        bar.setBackground(new Color(228, 234, 242));
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(10, 14));

        JPanel barWrap = new JPanel(new GridBagLayout());
        barWrap.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        barWrap.add(bar, gc);

        JLabel v = new JLabel(value);
        v.setFont(new Font(FONT, Font.BOLD, 16));
        v.setForeground(color.darker());
        v.setPreferredSize(new Dimension(45, 20));

        row.add(l, BorderLayout.WEST);
        row.add(barWrap, BorderLayout.CENTER);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    private void styleTable(JTable table) {
        table.setRowHeight(34);
        table.setFont(new Font(FONT, Font.PLAIN, 14));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(200, 225, 255));
        table.setSelectionForeground(Color.BLACK);

        JTableHeader th = table.getTableHeader();
        th.setPreferredSize(new Dimension(0, 36));
        th.setReorderingAllowed(false);
        th.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                l.setOpaque(true);
                l.setBackground(NAVY);
                l.setForeground(Color.WHITE);
                l.setFont(new Font(FONT, Font.BOLD, 13));
                l.setHorizontalAlignment(SwingConstants.CENTER);
                return l;
            }
        });

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                l.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                l.setHorizontalAlignment(SwingConstants.LEFT);
                l.setFont(new Font(FONT, Font.PLAIN, 14));
                if (!sel) {
                    l.setBackground(r % 2 == 0 ? Color.WHITE : new Color(243, 247, 253));
                    l.setForeground(new Color(40, 45, 65));
                }
                String s = String.valueOf(v);
                Color status = null;
                if (s.equals("Confirmed") || s.equals("Available") || s.equals("Active")) status = new Color(39, 174, 96);
                else if (s.equals("Pending") || s.equals("Booked")) status = new Color(230, 126, 34);
                else if (s.equals("Cancelled") || s.equals("Disabled")) status = new Color(231, 76, 60);
                else if (s.equals("Checked-out")) status = new Color(100, 110, 130);
                if (status != null) {
                    l.setFont(new Font(FONT, Font.BOLD, 14));
                    if (!sel) l.setForeground(status);
                }
                String col = t.getColumnName(c);
                if (col.startsWith("Days You Can Stay")) {
                    l.setFont(new Font(FONT, Font.BOLD, 14));
                    if (!sel) l.setForeground(new Color(22, 120, 180));
                } else if (col.startsWith("After Offer")) {
                    l.setFont(new Font(FONT, Font.BOLD, 14));
                    if (!sel) l.setForeground(new Color(39, 174, 96));
                }
                return l;
            }
        });
    }

    // =====================================================
    //                CUSTOM UI COMPONENTS
    // =====================================================
    static class GradientPanel extends JPanel {
        Color c1, c2; int radius;
        GradientPanel(Color c1, Color c2, int radius) {
            this.c1 = c1; this.c2 = c2; this.radius = radius;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class RoundedPanel extends JPanel {
        Color color; int radius;
        RoundedPanel(Color color, int radius) {
            this.color = color; this.radius = radius;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 20));
            g2.fillRoundRect(3, 4, getWidth() - 4, getHeight() - 4, radius, radius);
            g2.setColor(color);
            g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 4, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class RoundButton extends JButton {
        Color base; boolean over = false;
        RoundButton(String text, Color base) {
            super(text);
            this.base = base;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(new Font(FONT, Font.BOLD, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                public void mouseExited(MouseEvent e)  { over = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(over ? base.brighter() : base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class SideButton extends JButton {
        boolean active = false, over = false;
        SideButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(new Font(FONT, Font.BOLD, 15));
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 25, 0, 0));
            setPreferredSize(new Dimension(220, 48));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                public void mouseExited(MouseEvent e)  { over = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (active || over) {
                g2.setColor(HOVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            if (active) {
                g2.setColor(ORANGE);
                g2.fillRect(0, 0, 6, getHeight());
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HotelDashboard());
    }
}
