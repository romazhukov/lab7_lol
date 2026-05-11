package org.lab5.commands.impl;

import org.lab5.commands.AbstractCommand;
import org.lab5.commands.CommandContext;
import org.lab5.managers.CollectionManager;

public class InfoCommand extends AbstractCommand {

    public InfoCommand() {
        super("info", "print information about the collection");
    }

    @Override
    public void execute(CommandContext ctx, String[] args) {
        CollectionManager manager = ctx.getCollectionManager();
        ctx.getConsole().println("Collection type: " + manager.getCollectionTypeName());
        ctx.getConsole().println("Sorting: annualTurnover ascending, employeesCount ascending, name ascending, id ascending");
        ctx.getConsole().println("Initialization time: " + manager.getInitializationTime());
        ctx.getConsole().println("Elements count: " + manager.size());
    }
}
