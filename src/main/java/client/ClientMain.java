package client;

import org.lab5.exceptions.CommandExecutionException;
import org.lab5.io.InputManager;
import org.lab5.io.OrganizationBuilder;
import org.lab5.io.StandardConsole;
import org.lab5.managers.ScriptManager;
import org.lab5.models.Address;
import org.lab5.models.Organization;
import org.lab5.models.OrganizationType;
import shared.CommandRequest;
import shared.CommandResponse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientMain {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5555;
    private static String currentUsername;
    private static String currentPassword;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        StandardConsole console = new StandardConsole();
        ScriptManager scriptManager = new ScriptManager();
        InputManager inputManager = new InputManager(console, scriptManager);
        OrganizationBuilder builder = new OrganizationBuilder(console, inputManager);

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            console.println("Connected to server " + host + ":" + port);

            while (true) {
                console.print("$ ");
                String line = inputManager.readLine();

                if (line == null || line.trim().equals("exit")) {
                    break;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    CommandRequest request = makeRequest(line, builder, out, in);
                    CommandResponse response = send(out, in, request);
                    print(console, response);
                    updateCredentials(request, response);
                } catch (Exception e) {
                    console.printError(e.getMessage());
                }
            }
        } catch (IOException e) {
            console.printError("Server is unavailable: " + e.getMessage());
        }
    }

    private static CommandRequest makeRequest(
            String line,
            OrganizationBuilder builder,
            ObjectOutputStream out,
            ObjectInputStream in
    ) throws Exception {
        ParsedCommand parsed = parse(line);

        String name = parsed.name;
        String[] args = parsed.args;

        Organization.Draft draft = null;
        Integer targetId = null;
        Address address = null;
        OrganizationType type = null;

        if (name.equals("add") || name.equals("add_if_min") || name.equals("remove_lower")) {
            draft = builder.readOrganizationDraft();
        }

        if (name.equals("update")) {
            if (args.length != 1) {
                throw new CommandExecutionException("usage: update <id>");
            }

            targetId = parseId(args[0]);

            CommandRequest getRequest = new CommandRequest(
                    "__internal_get_by_id",
                    new String[0],
                    null,
                    targetId,
                    null,
                    null,
                    currentUsername,
                    currentPassword
            );

            CommandResponse response = send(out, in, getRequest);

            if (!response.isSuccess()) {
                throw new CommandExecutionException(response.getMessage());
            }

            Organization oldOrganization = (Organization) response.getData();
            draft = builder.readOrganizationDraftForUpdate(oldOrganization);
        }

        if (name.equals("remove_all_by_postal_address")) {
            address = builder.readAddressFilter();
        }

        if (name.equals("count_greater_than_type")) {
            if (args.length != 1) {
                throw new CommandExecutionException("usage: count_greater_than_type <type>");
            }

            try {
                type = OrganizationType.valueOf(args[0].trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CommandExecutionException("unknown OrganizationType: " + args[0]);
            }
        }

        return new CommandRequest(
                name,
                args,
                draft,
                targetId,
                address,
                type,
                currentUsername,
                currentPassword
        );
    }

    private static ParsedCommand parse(String line) {
        if (line.startsWith("execute_script")) {
            String rest = line.substring("execute_script".length()).trim();
            String[] args = rest.isEmpty() ? new String[0] : new String[]{rest};
            return new ParsedCommand("execute_script", args);
        }

        String[] parts = line.split("\\s+");
        String name = parts[0];
        String[] args = new String[parts.length - 1];

        for (int i = 1; i < parts.length; i++) {
            args[i - 1] = parts[i];
        }

        return new ParsedCommand(name, args);
    }

    private static int parseId(String text) throws CommandExecutionException {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new CommandExecutionException("id must be an integer");
        }
    }

    private static CommandResponse send(
            ObjectOutputStream out,
            ObjectInputStream in,
            CommandRequest request
    ) throws IOException, ClassNotFoundException {
        out.writeObject(request);
        out.flush();

        Object response = in.readObject();

        if (response instanceof CommandResponse commandResponse) {
            return commandResponse;
        }

        return CommandResponse.error("Unexpected response from server");
    }

    private static void print(StandardConsole console, CommandResponse response) {
        if (response.isSuccess()) {
            console.println(response.getMessage());
        } else {
            console.printError(response.getMessage());
        }
    }

    private static void updateCredentials(CommandRequest request, CommandResponse response) {
        if (!response.isSuccess()) {
            return;
        }
        if (!"register".equals(request.getName()) && !"login".equals(request.getName())) {
            return;
        }
        if (request.getArgs().length < 2) {
            return;
        }
        currentUsername = request.getArgs()[0];
        currentPassword = request.getArgs()[1];
    }

    private static class ParsedCommand {
        private final String name;
        private final String[] args;

        private ParsedCommand(String name, String[] args) {
            this.name = name;
            this.args = args;
        }
    }
}