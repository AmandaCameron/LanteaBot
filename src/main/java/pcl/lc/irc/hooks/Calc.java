package pcl.lc.irc.hooks;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import org.apache.commons.lang3.StringUtils;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

import pcl.lc.irc.AbstractListener;
import pcl.lc.irc.Config;
import pcl.lc.irc.IRCBot;

/**
 * @author Caitlyn
 *
 */
@SuppressWarnings("rawtypes")
public class Calc extends AbstractListener {
	
	@Override
	protected void initCommands() {
		IRCBot.registerCommand("calc");
	}

	@Override
	public void handleCommand(String sender, final MessageEvent event, String command, String[] args) {
		String prefix = Config.commandprefix;
		super.onMessage(event);

		String ourinput = event.getMessage().toLowerCase();
		String trigger = ourinput.trim();
		String[] splitMessage = event.getMessage().split(" ");
		if (trigger.length() > 1) {
			String[] firstWord = StringUtils.split(trigger);
			String triggerWord = firstWord[0];
			if (command.equals(prefix + "calc") || splitMessage[0].startsWith("<") && splitMessage[0].endsWith(">") && splitMessage[1].equals(prefix + "calc")) {
				String[] message = event.getMessage().split(" ");
				String expression;
				if (splitMessage[0].startsWith("<") && splitMessage[0].endsWith(">")) {
					expression = message[2].trim();
				} else {
					expression = message[1].trim();
				}
				if (!IRCBot.isIgnored(event.getUser().getNick())) {
					if (expression.equalsIgnoreCase("the meaning of life")) {
						event.respond("42");
					} else {
						Expression e = new ExpressionBuilder(expression).build();
						double result = e.evaluate();
						NumberFormat formatter = new DecimalFormat("#,###.##");
						event.respond(formatter.format(result));
					}
				}
			}	
		}
	}
}