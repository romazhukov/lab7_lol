package server;

import org.lab5.commands.CommandContext;
import org.lab5.commands.CommandManager;
import org.lab5.commands.impl.*;
import org.lab5.db.DatabaseManager;
import org.lab5.db.OrganizationDao;
import org.lab5.db.UserDao;
import org.lab5.io.Console;
import org.lab5.io.InputManager;
import org.lab5.managers.CollectionManager;
import org.lab5.managers.IdGenerator;
import org.lab5.managers.ScriptManager;
import org.lab5.models.Organization;
import org.lab5.models.OrganizationType;
import org.lab5.security.PasswordHasher;
import shared.CommandRequest;
import shared.CommandResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerMain {
    private static final int DEFAULT_PORT = 5555;
    private static final Set<String> AUTH_FREE_COMMANDS = Set.of("register", "login", "help", "exit");

    public static void main(String[] args) {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        DatabaseManager databaseManager = new DatabaseManager();
        UserDao userDao = new UserDao(databaseManager);
        OrganizationDao organizationDao = new OrganizationDao(databaseManager);

        try {
            databaseManager.initializeSchema();
        } catch (Exception e) {
            System.err.println("Database init error: " + message(e));
            return;
        }

        CollectionManager collectionManager = new CollectionManager();
        try {
            collectionManager.replaceAll(organizationDao.findAll());
        } catch (Exception e) {
            System.err.println("Database load error: " + message(e));
            return;
        }

        ScriptManager scriptManager = new ScriptManager();
        BufferConsole console = new BufferConsole();
        InputManager inputManager = new InputManager(console, scriptManager);
        CommandContext context = new CommandContext(
                collectionManager,
                null,
                inputManager,
                scriptManager,
                console,
                new IdGenerator(),
                new AtomicBoolean(true)
        );
        CommandManager commandManager = createCommandManager();
        commandManager.setContext(context);

        ExecutorService connectPool = Executors.newCachedThreadPool();
        ExecutorService requestPool = Executors.newFixedThreadPool(8);
        ExecutorService responsePool = Executors.newFixedThreadPool(8);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.err.println("Server started on localhost:" + port);
            while (true) {
                Socket socket = serverSocket.accept();
                connectPool.submit(() -> handleClient(
                        socket,
                        commandManager,
                        context,
                        userDao,
                        organizationDao,
                        requestPool,
                        responsePool
                ));
            }
        } catch (Exception e) {
            System.err.println("Server error: " + message(e));
        }
    }

    private static void handleClient(
            Socket socket,
            CommandManager commandManager,
            CommandContext context,
            UserDao userDao,
            OrganizationDao organizationDao,
            ExecutorService requestPool,
            ExecutorService responsePool
    ) {
        try (Socket client = socket;
             ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
            while (true) {
                try {
                    Object object = in.readObject();
                    CommandResponse response;
                    if (object instanceof CommandRequest request) {
                        response = requestPool.submit(() -> process(request, commandManager, context, userDao, organizationDao)).get();
                    } else {
                        response = CommandResponse.error("Unexpected request type");
                    }
                    responsePool.submit(() -> {
                        synchronized (out) {
                            try {
                                out.writeObject(response);
                                out.flush();
                            } catch (IOException ignored) {
                            }
                        }
                    }).get();
                } catch (EOFException e) {
                    break;
                } catch (ClassNotFoundException e) {
                    out.writeObject(CommandResponse.error("Unknown request class"));
                    out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("Client error: " + message(e));
        }
    }

    private static CommandResponse process(
            CommandRequest request,
            CommandManager commandManager,
            CommandContext context,
            UserDao userDao,
            OrganizationDao organizationDao
    ) {
        try {
            String name = request.getName();

            if (name == null || name.isBlank()) {
                return CommandResponse.error("Command name must not be empty");
            }

            AuthUser authUser = auth(request, userDao, AUTH_FREE_COMMANDS.contains(name));
            if (authUser == null && !AUTH_FREE_COMMANDS.contains(name)) {
                return CommandResponse.error("authorization failed");
            }

            switch (name) {
                case "register":
                    return register(request, userDao);
                case "login":
                    return login(request, userDao);
                case "add":
                    return add(request, context, organizationDao, authUser);
                case "update":
                    return update(request, context, organizationDao, authUser);
                case "add_if_min":
                    return addIfMin(request, context, organizationDao, authUser);
                case "remove_lower":
                    return removeLower(request, context, organizationDao, authUser);
                case "remove_all_by_postal_address":
                    return removeAllByPostalAddress(request, context, organizationDao, authUser);
                case "remove_by_id":
                    return removeById(request, context, organizationDao, authUser);
                case "__internal_get_by_id":
                    return getById(request, context);
                case "clear":
                    return clear(context, organizationDao, authUser);
                case "save":
                    return CommandResponse.error("save is disabled");
                case "remove_head":
                    return removeHead(context, organizationDao, authUser);
                case "count_greater_than_type":
                    return countGreaterThanType(request, context);
                case "show":
                case "info":
                case "help":
                case "execute_script":
                case "print_field_descending_postal_address":
                    return runOldCommand(name, request.getArgs(), commandManager, context);
                default:
                    return CommandResponse.error("Unknown command: " + name);
            }
        } catch (Exception e) {
            return CommandResponse.error("Server error: " + message(e));
        }
    }

    private static CommandResponse register(CommandRequest request, UserDao userDao) {
        if (request.getArgs().length != 2) {
            return CommandResponse.error("usage: register <login> <password>");
        }
        try {
            String username = request.getArgs()[0];
            String password = request.getArgs()[1];
            boolean created = userDao.register(username, PasswordHasher.sha224(password));
            if (!created) {
                return CommandResponse.error("user already exists");
            }
            return CommandResponse.ok("registered");
        } catch (Exception e) {
            return CommandResponse.error("register failed: " + message(e));
        }
    }

    private static CommandResponse login(CommandRequest request, UserDao userDao) {
        if (request.getArgs().length != 2) {
            return CommandResponse.error("usage: login <login> <password>");
        }
        try {
            String username = request.getArgs()[0];
            String password = request.getArgs()[1];
            Integer userId = userDao.findUserIdByCredentials(username, PasswordHasher.sha224(password));
            if (userId == null) {
                return CommandResponse.error("wrong login or password");
            }
            return CommandResponse.ok("logged in");
        } catch (Exception e) {
            return CommandResponse.error("login failed: " + message(e));
        }
    }

    private static CommandResponse add(CommandRequest request, CommandContext context, OrganizationDao organizationDao, AuthUser authUser) {
        if (request.getDraft() == null) {
            return CommandResponse.error("Command add requires draft");
        }

        try {
            request.getDraft().validate();
            Organization organization = organizationDao.insert(request.getDraft(), authUser.userId(), authUser.username());
            context.getCollectionManager().addExisting(organization);
            return CommandResponse.ok("Added organization id=" + organization.getId(), organization.getId());
        } catch (Exception e) {
            return CommandResponse.error("Command add failed: " + message(e));
        }
    }

    private static CommandResponse update(CommandRequest request, CommandContext context, OrganizationDao organizationDao, AuthUser authUser) {
        if (request.getTargetId() == null) {
            return CommandResponse.error("Command update requires id");
        }

        if (request.getDraft() == null) {
            return CommandResponse.error("Command update requires draft");
        }

        try {
            Organization old = context.getCollectionManager().findById(request.getTargetId());
            if (old == null) {
                return CommandResponse.error("organization with id=" + request.getTargetId() + " not found");
            }
            if (!authUser.userId().equals(old.getOwnerId())) {
                return CommandResponse.error("permission denied");
            }
            boolean updatedInDb = organizationDao.update(request.getTargetId(), request.getDraft(), authUser.userId(), authUser.username());
            if (!updatedInDb) {
                return CommandResponse.error("permission denied");
            }
            Organization organization = context.getCollectionManager().update(request.getTargetId(), request.getDraft());
            return CommandResponse.ok("Updated organization id=" + organization.getId(), organization.getId());
        } catch (Exception e) {
            return CommandResponse.error("Command update failed: " + message(e));
        }
    }

    private static CommandResponse addIfMin(CommandRequest request, CommandContext context, OrganizationDao organizationDao, AuthUser authUser) {
        if (request.getDraft() == null) {
            return CommandResponse.error("Command add_if_min requires draft");
        }

        try {
            List<Organization> snapshot = context.getCollectionManager().snapshotAll();
            boolean isMin = snapshot.isEmpty();
            if (!isMin) {
                Organization min = snapshot.stream().min(Organization::compareTo).orElse(null);
                Organization probe = request.getDraft().toOrganization(Integer.MAX_VALUE, ZonedDateTime.now());
                isMin = min != null && probe.compareTo(min) < 0;
            }
            if (!isMin) {
                return CommandResponse.ok("Element is not minimal, nothing added");
            }
            Organization organization = organizationDao.insert(request.getDraft(), authUser.userId(), authUser.username());
            context.getCollectionManager().addExisting(organization);
            return CommandResponse.ok("Added organization id=" + organization.getId(), organization.getId());
        } catch (Exception e) {
            return CommandResponse.error("Command add_if_min failed: " + message(e));
        }
    }

    private static CommandResponse removeLower(CommandRequest request, CommandContext context, OrganizationDao organizationDao, AuthUser authUser) {
        if (request.getDraft() == null) {
            return CommandResponse.error("Command remove_lower requires draft");
        }

        try {
            request.getDraft().validate();
            Organization threshold = request.getDraft().toOrganization(Integer.MAX_VALUE, ZonedDateTime.now());
            int removedDb = organizationDao.deleteLowerThan(threshold, authUser.userId());
            int removed = context.getCollectionManager().removeLowerByOwner(threshold, authUser.userId());
            if (removedDb != removed) {
                refreshFromDb(context, organizationDao);
            }
            return CommandResponse.ok("Removed elements: " + removed, removed);
        } catch (Exception e) {
            return CommandResponse.error("Command remove_lower failed: " + message(e));
        }
    }

    private static CommandResponse removeAllByPostalAddress(CommandRequest request, CommandContext context, OrganizationDao organizationDao, AuthUser authUser) {
        try {
            int removedDb = organizationDao.deleteAllByPostalAddress(request.getAddress(), authUser.userId());
            int removed = context.getCollectionManager().removeAllByPostalAddressAndOwner(request.getAddress(), authUser.userId());
            if (removedDb != removed) {
                refreshFromDb(context, organizationDao);
            }
            return CommandResponse.ok("Removed elements: " + removed, removed);
        } catch (Exception e) {
            return CommandResponse.error("Command remove_all_by_postal_address failed: " + message(e));
        }
    }

    private static CommandResponse clear(CommandContext context, OrganizationDao organizationDao, AuthUser authUser) {
        try {
            organizationDao.deleteAllByOwner(authUser.userId());
            int removed = context.getCollectionManager().removeByOwner(authUser.userId());
            return CommandResponse.ok("Collection cleared, removed: " + removed);
        } catch (Exception e) {
            return CommandResponse.error("Command clear failed: " + message(e));
        }
    }

    private static CommandResponse removeById(CommandRequest request, CommandContext context, OrganizationDao organizationDao, AuthUser authUser) {
        int id;
        if (request.getTargetId() != null) {
            id = request.getTargetId();
        } else if (request.getArgs().length == 1) {
            id = parseId(request.getArgs()[0]);
        } else {
            return CommandResponse.error("usage: remove_by_id <id>");
        }
        Organization existing = context.getCollectionManager().findById(id);
        if (existing == null) {
            return CommandResponse.error("organization with id=" + id + " not found");
        }
        if (!authUser.userId().equals(existing.getOwnerId())) {
            return CommandResponse.error("permission denied");
        }
        try {
            boolean removedDb = organizationDao.deleteById(id, authUser.userId());
            if (!removedDb) {
                return CommandResponse.error("permission denied");
            }
            context.getCollectionManager().removeById(id);
            return CommandResponse.ok("Removed organization id=" + id, id);
        } catch (Exception e) {
            return CommandResponse.error("Command remove_by_id failed: " + message(e));
        }
    }

    private static CommandResponse getById(CommandRequest request, CommandContext context) {
        if (request.getTargetId() == null) {
            return CommandResponse.error("internal get_by_id requires id");
        }
        int id = request.getTargetId();
        Organization organization = context.getCollectionManager().findById(id);
        if (organization == null) {
            return CommandResponse.error("organization with id=" + id + " not found");
        }
        return CommandResponse.ok("Found organization id=" + id, organization);
    }

    private static CommandResponse removeHead(CommandContext context, OrganizationDao organizationDao, AuthUser authUser) {
        Organization head = context.getCollectionManager().getSortedView().stream()
                .filter(o -> authUser.userId().equals(o.getOwnerId()))
                .findFirst()
                .orElse(null);
        if (head == null) {
            return CommandResponse.ok("collection is empty");
        }
        try {
            boolean removed = organizationDao.deleteById(head.getId(), authUser.userId());
            if (!removed) {
                return CommandResponse.error("permission denied");
            }
            context.getCollectionManager().removeById(head.getId());
            return CommandResponse.ok("Removed head: " + head, head);
        } catch (Exception e) {
            return CommandResponse.error("Command remove_head failed: " + message(e));
        }
    }

    private static CommandResponse countGreaterThanType(CommandRequest request, CommandContext context) {
        OrganizationType type = request.getOrganizationType();
        if (type == null) {
            if (request.getArgs().length != 1) {
                return CommandResponse.error("usage: count_greater_than_type <type>");
            }
            try {
                type = OrganizationType.valueOf(request.getArgs()[0].trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return CommandResponse.error("unknown OrganizationType: " + request.getArgs()[0]);
            }
        }
        int count = context.getCollectionManager().countGreaterThanType(type);
        return CommandResponse.ok("count = " + count, count);
    }

    private static CommandResponse runOldCommand(
            String name,
            String[] args,
            CommandManager commandManager,
            CommandContext context
    ) {
        BufferConsole console = (BufferConsole) context.getConsole();
        console.reset();

        commandManager.handle(buildLine(name, args));

        if (console.hasErrors()) {
            return CommandResponse.error(console.read());
        }

        return CommandResponse.ok(console.read());
    }

    private static String buildLine(String name, String[] args) {
        if (args == null || args.length == 0) {
            return name;
        }

        return name + " " + String.join(" ", args);
    }

    private static int parseId(String value) {
        return Integer.parseInt(value.trim());
    }

    private static CommandManager createCommandManager() {
        CommandManager manager = new CommandManager();

        manager.register(new HelpCommand());
        manager.register(new InfoCommand());
        manager.register(new ShowCommand());
        manager.register(new AddCommand());
        manager.register(new UpdateCommand());
        manager.register(new RemoveByIdCommand());
        manager.register(new ClearCommand());
        manager.register(new ExecuteScriptCommand());
        manager.register(new ExitCommand());
        manager.register(new RemoveHeadCommand());
        manager.register(new AddIfMinCommand());
        manager.register(new RemoveLowerCommand());
        manager.register(new SaveCommand());
        manager.register(new RemoveAllByPostalAddressCommand());
        manager.register(new CountGreaterThanTypeCommand());
        manager.register(new PrintFieldDescendingPostalAddressCommand());

        return manager;
    }

    private static String message(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getMessage();
    }

    private static void refreshFromDb(CommandContext context, OrganizationDao organizationDao) throws Exception {
        context.getCollectionManager().replaceAll(organizationDao.findAll());
    }

    private static AuthUser auth(CommandRequest request, UserDao userDao, boolean canSkip) throws Exception {
        if (canSkip) {
            return null;
        }
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        Integer userId = userDao.findUserIdByCredentials(username, PasswordHasher.sha224(password));
        if (userId == null) {
            return null;
        }
        return new AuthUser(userId, username);
    }

    private record AuthUser(Integer userId, String username) {
    }

    private static class BufferConsole implements Console {
        private final StringBuilder text = new StringBuilder();
        private boolean hasErrors;

        @Override
        public void print(String value) {
            text.append(value);
        }

        @Override
        public void println(String value) {
            text.append(value).append(System.lineSeparator());
        }

        @Override
        public void printError(String value) {
            hasErrors = true;
            text.append(value).append(System.lineSeparator());
        }

        @Override
        public String readLineInteractive() {
            return null;
        }

        public void reset() {
            text.setLength(0);
            hasErrors = false;
        }

        public boolean hasErrors() {
            return hasErrors;
        }

        public String read() {
            String result = text.toString().trim();
            if (result.isEmpty()) {
                return "OK";
            }
            return result;
        }
    }
}