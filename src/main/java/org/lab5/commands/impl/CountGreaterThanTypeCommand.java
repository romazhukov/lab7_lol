package org.lab5.commands.impl;

import org.lab5.commands.AbstractCommand;
import org.lab5.commands.CommandContext;
import org.lab5.models.OrganizationType;

public class CountGreaterThanTypeCommand extends AbstractCommand {

    public CountGreaterThanTypeCommand() {
        super("count_greater_than_type", "count elements with type greater than given type or null");
    }

    @Override
    public void execute(CommandContext ctx, String[] args) {
        if (args.length != 1) {
            ctx.getConsole().printError("usage: count_greater_than_type <type>");
            return;
        }

        OrganizationType type;
        if (args[0].trim().equalsIgnoreCase("null")) {
            type = null;
        } else {
            try {
                type = OrganizationType.valueOf(args[0].trim().toUpperCase());
            } catch (Exception e) {
                ctx.getConsole().printError("unknown type: " + args[0]);
                return;
            }
        }

        int count = ctx.getCollectionManager().countGreaterThanType(type);
        ctx.getConsole().println("count = " + count);
    }
}
