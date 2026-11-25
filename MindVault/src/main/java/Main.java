import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final Scanner in = new Scanner(System.in);
    private static final ScheduledExecutorService alerts =
        Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });

    public static void main(String[] args) {
        System.out.println("""
        ╔══════════════════════════════════════════════╗
        ║              M I N D V A U L T               ║
        ║       Capture  •  Connect  •  Remember       ║
        ╚══════════════════════════════════════════════╝
        """);
        try (Connection ignored = Database.connect()) {
            System.out.println("✓ MySQL connected");
            reminders();
            alerts.scheduleAtFixedRate(Main::reminders, 30, 30, TimeUnit.SECONDS);
            menu();
        } catch (Exception e) { System.out.println("Startup error: " + e.getMessage()); }
        finally { alerts.shutdownNow(); }
    }

    private static String ask(String s) { System.out.print(s); return in.nextLine().trim(); }
    private static long id(String s) { try { return Long.parseLong(ask(s)); } catch (Exception e) { return -1; } }
    private static void menu() {
        while (true) {
            System.out.println("""
            ┌────────────── MAIN MENU ─────────────────┐
            │ 1 Quick capture       2 Search notes     │
            │ 3 Browse / open       4 Pin note         │
            │ 5 Link notes          6 Add task         │
            │ 7 View / complete     8 Review queue     │
            │ 9 Dashboard            0 Exit             │
            └─────────────────────────────────────────┘
            """);
            switch (ask("Choose: ")) {
                case "1" -> addNote(); case "2" -> search(); case "3" -> browse();
                case "4" -> pin(); case "5" -> link(); case "6" -> addTask();
                case "7" -> tasks(); case "8" -> review(); case "9" -> dashboard();
                case "0" -> { System.out.println("See you!"); return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static void addNote() {
        String title=ask("Title: "), category=ask("Category [General]: ");
        String content=ask("Content (one line): "), tags=ask("Tags, comma separated: ");
        if (title.isBlank() || content.isBlank()) { System.out.println("Title/content required."); return; }
        if (category.isBlank()) category="General";
        try (Connection c=Database.connect()) {
            c.setAutoCommit(false);
            try {
                long noteId;
                try (PreparedStatement p=c.prepareStatement("INSERT INTO notes(title,category,content) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1,title); p.setString(2,category); p.setString(3,content); p.executeUpdate();
                    try (ResultSet k=p.getGeneratedKeys()) { k.next(); noteId=k.getLong(1); }
                }
                for (String raw:tags.split(",")) {
                    String tag=raw.trim().toLowerCase(); if (tag.isBlank()) continue;
                    try (PreparedStatement p=c.prepareStatement("INSERT INTO tags(name) VALUES(?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",Statement.RETURN_GENERATED_KEYS)) {
                        p.setString(1,tag); p.executeUpdate();
                        try (ResultSet k=p.getGeneratedKeys()) {
                            if (k.next()) try (PreparedStatement x=c.prepareStatement("INSERT IGNORE INTO note_tags VALUES(?,?)")) {
                                x.setLong(1,noteId); x.setLong(2,k.getLong(1)); x.executeUpdate();
                            }
                        }
                    }
                }
                c.commit(); System.out.println("✓ Note saved as #" + noteId);
            } catch (Exception e) { c.rollback(); System.out.println("Save failed; rolled back: "+e.getMessage()); }
        } catch (SQLException e) { error(e); }
    }

    private static void search() {
        String q="%"+ask("Search title/content/category/tag: ")+"%";
        String sql="SELECT DISTINCT n.id,n.title,n.category,n.pinned FROM notes n LEFT JOIN note_tags nt ON n.id=nt.note_id LEFT JOIN tags t ON nt.tag_id=t.id WHERE n.title LIKE ? OR n.content LIKE ? OR n.category LIKE ? OR t.name LIKE ? ORDER BY n.pinned DESC,n.updated_at DESC";
        try (Connection c=Database.connect(); PreparedStatement p=c.prepareStatement(sql)) {
            for(int i=1;i<=4;i++) p.setString(i,q);
            try(ResultSet r=p.executeQuery()){ while(r.next()) show(r); }
        } catch(SQLException e){error(e);}
    }

    private static void browse() {
        try(Connection c=Database.connect(); Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT id,title,category,pinned FROM notes ORDER BY pinned DESC,updated_at DESC")) {
            while(r.next()) show(r);
            long n=id("Open note ID (0 back): "); if(n==0)return;
            try(PreparedStatement p=c.prepareStatement("SELECT * FROM notes WHERE id=?")) {
                p.setLong(1,n); try(ResultSet x=p.executeQuery()) {
                    if(x.next()) System.out.printf("%n#%d %s [%s]%n%s%nCreated: %s%nUpdated: %s%n",
                        x.getLong("id"),x.getString("title"),x.getString("category"),x.getString("content"),
                        x.getTimestamp("created_at"),x.getTimestamp("updated_at"));
                    else System.out.println("Note not found.");
                }
            }
        }catch(SQLException e){error(e);}
    }
    private static void show(ResultSet r)throws SQLException {
        System.out.printf("#%-4d %-30s [%s]%s%n",r.getLong("id"),r.getString("title"),r.getString("category"),r.getBoolean("pinned")?" ★":"");
    }
    private static void pin() {
        long n=id("Note ID: ");
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("UPDATE notes SET pinned=NOT pinned WHERE id=?")){
            p.setLong(1,n);System.out.println(p.executeUpdate()==1?"Pin updated.":"Note not found.");
        }catch(SQLException e){error(e);}
    }
    private static void link() {
        long a=id("First note ID: "),b=id("Second note ID: ");
        if(a==b){System.out.println("Cannot link a note to itself.");return;}
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO note_links VALUES(?,?)")){
            p.setLong(1,a);p.setLong(2,b);System.out.println(p.executeUpdate()==1?"Notes linked.":"Link exists or ID invalid.");
        }catch(SQLException e){error(e);}
    }
    private static void addTask() {
        String title=ask("Task: "), date=ask("Due date YYYY-MM-DD (blank none): "), priority=ask("Priority LOW/MEDIUM/HIGH [MEDIUM]: ").toUpperCase();
        if(priority.isBlank())priority="MEDIUM";
        if(title.isBlank()||!List.of("LOW","MEDIUM","HIGH").contains(priority)){System.out.println("Invalid title/priority.");return;}
        try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO tasks(title,due_date,priority) VALUES(?,?,?)")){
            p.setString(1,title);if(date.isBlank())p.setNull(2,Types.DATE);else p.setDate(2,java.sql.Date.valueOf(LocalDate.parse(date)));
            p.setString(3,priority);p.executeUpdate();System.out.println("✓ Task added.");
        }catch(Exception e){System.out.println("Invalid date/database error: "+e.getMessage());}
    }
    private static void tasks() {
        try(Connection c=Database.connect();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM tasks ORDER BY status,due_date")){
            while(r.next())System.out.printf("#%d %-25s %-6s %-8s %s%n",r.getLong("id"),r.getString("title"),r.getString("priority"),r.getString("status"),r.getDate("due_date"));
            long n=id("Task ID to mark DONE (0 back): ");if(n==0)return;
            try(PreparedStatement p=c.prepareStatement("UPDATE tasks SET status='DONE',notified=TRUE WHERE id=? AND status='PENDING'")){
                p.setLong(1,n);System.out.println(p.executeUpdate()==1?"Task completed.":"Pending task not found.");
            }
        }catch(SQLException e){error(e);}
    }
    private static void review() {
        try(Connection c=Database.connect();Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT id,title,next_review,review_count FROM notes WHERE next_review<=CURDATE() ORDER BY next_review")){
            boolean any=false;while(r.next()){any=true;System.out.printf("#%d %s | reviewed %d times | due %s%n",r.getLong(1),r.getString(2),r.getInt(4),r.getDate(3));}
            if(!any)System.out.println("No notes due for review. Set a first review date for a note in Workbench.");
            long n=id("Reviewed note ID (0 back): ");if(n==0)return;
            try(PreparedStatement p=c.prepareStatement("UPDATE notes SET review_count=review_count+1,next_review=DATE_ADD(CURDATE(),INTERVAL 7 DAY) WHERE id=?")){
                p.setLong(1,n);System.out.println(p.executeUpdate()==1?"Reviewed. Next review in 7 days.":"Note not found.");
            }
        }catch(SQLException e){error(e);}
    }
    private static void dashboard() {
        String q="SELECT (SELECT COUNT(*) FROM notes) notes,(SELECT COUNT(*) FROM notes WHERE pinned=1) pins,(SELECT COUNT(*) FROM tasks WHERE status='PENDING') pending,(SELECT COUNT(*) FROM tasks WHERE status='DONE') done";
        try(Connection c=Database.connect();Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){
            r.next();System.out.printf("%n══════ MINDVAULT DASHBOARD ══════%nNotes: %d | Pinned: %d%nPending tasks: %d | Completed: %d%n",
                r.getInt("notes"),r.getInt("pins"),r.getInt("pending"),r.getInt("done"));
        }catch(SQLException e){error(e);}
    }
    private static void reminders() {
        String q="SELECT id,title,due_date FROM tasks WHERE status='PENDING' AND due_date<=CURDATE() AND notified=FALSE";
        try(Connection c=Database.connect();Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){
            List<Long> ids=new ArrayList<>();
            while(r.next()){
                String state=r.getDate("due_date").toLocalDate().isBefore(LocalDate.now())?"OVERDUE":"DUE TODAY";
                System.out.printf("%n🔔 REMINDER: %s — %s (task #%d)%n",state,r.getString("title"),r.getLong("id"));ids.add(r.getLong("id"));
            }
            for(long n:ids)try(PreparedStatement p=c.prepareStatement("UPDATE tasks SET notified=TRUE WHERE id=?")){p.setLong(1,n);p.executeUpdate();}
        }catch(SQLException e){System.out.println("Reminder check unavailable: "+e.getMessage());}
    }
    private static void error(SQLException e){System.out.println("Database error: "+e.getMessage());}
}