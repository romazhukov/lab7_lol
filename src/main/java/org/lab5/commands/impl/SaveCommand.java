package org.lab5.commands.impl;

import org.lab5.commands.AbstractCommand;
import org.lab5.commands.CommandContext;
import org.lab5.exceptions.CommandExecutionException;

public class SaveCommand extends AbstractCommand {

    public SaveCommand() {
        super("save", "save collection to file");
    }

    @Override
    public void execute(CommandContext ctx, String[] args) throws CommandExecutionException {
        throw new CommandExecutionException("save is disabled, data is stored in database automatically");
    }
}