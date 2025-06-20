package de.kaleidox.kanna;

import de.kaleidox.kanna.entity.RegisteredEmoji;
import de.kaleidox.kanna.repo.EmojiRegistry;
import lombok.Value;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.comroid.api.func.Clearable;
import org.comroid.api.func.exc.ThrowingFunction;
import org.comroid.api.info.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.kaleidox.kanna.util.ApplicationContextProvider.*;
import static org.comroid.api.attr.Named.*;
import static org.comroid.api.text.Translation.*;

@Value
public class EmojiManagementController extends ListenerAdapter implements Clearable {
    Pattern              CUSTOM_EMOJI_TAG = Pattern.compile("<(a?):(\\w+):(\\d+)>");
    BotConfig            config;
    JDA                  jda;
    Set<RegisteredEmoji> importQueue      = new HashSet<>();
    Set<RegisteredEmoji> selected         = new HashSet<>();

    public EmojiManagementController(BotConfig config, JDA jda) {
        this.config = config;
        this.jda    = jda;

        jda.addEventListener(this);

        jda.upsertCommand(Commands.context(Command.Type.MESSAGE, str(Key.COMMAND_EMOJI_IMPORT_NAME))).queue();
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        if (!str(Key.COMMAND_EMOJI_IMPORT_NAME).equals(event.getName())) return;

        event.deferReply().setEphemeral(true).flatMap(hook -> {
            var msg  = event.getTarget();
            var menu = StringSelectMenu.create(Key.COMMAND_EMOJI_IMPORT_MENU);
            var mesg = new MessageCreateBuilder();

            clear();
            Stream.of(CUSTOM_EMOJI_TAG.matcher(msg.getContentRaw())
                                    .results()
                                    .map(result -> Emoji.fromCustom(result.group(2),
                                            Long.parseLong(result.group(3)),
                                            "a".equals(result.group(1))))
                                    .map(RegisteredEmoji::of),
                            msg.getAttachments().stream().map(RegisteredEmoji::of),
                            msg.getStickers().stream().map(RegisteredEmoji::of))
                    .flatMap(Function.identity())
                    .peek(emoji -> {
                        var name = emoji.getName();
                        if (!name.matches("\\w+")) emoji.setName(name.replaceAll("\\W", ""));
                    })
                    .map(emoji -> emoji.setImportedBy(event.getUser().getIdLong())
                            .setImportedFrom(Stream.ofNullable(event.getChannel())
                                    .map(ThrowingFunction.fallback(MessageChannelUnion::asGuildMessageChannel))
                                    .findAny()
                                    .map(GuildChannel::getGuild)
                                    .map(ISnowflake::getIdLong)
                                    .orElseGet(event::getChannelIdLong)))
                    .peek(emoji -> {
                        importQueue.add(emoji);
                        if (!emoji.isExported()) mesg.addEmbeds(emoji.asEmbed());
                    })
                    .map(RegisteredEmoji::asSelectOption)
                    .forEach(menu::addOptions);
            menu.setMaxValues(menu.getOptions().size());

            return hook.sendMessage(mesg.setContent(str(Key.COMMAND_EMOJI_IMPORT_MENU_TITLE)).addActionRow(menu.build()
                    //, Button.secondary(Key.RENAME, str(Key.RENAME)), Button.success(Key.APPLY, str(Key.APPLY))
            ).build());
        }).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case Key.RENAME:
                var modal = Modal.create(Key.RENAME, str(Key.COMMAND_EMOJI_IMPORT_RENAME_TITLE));

                // mapping list
                if (selected.size() <= 5) for (var emoji : selected)
                    modal.addActionRow(TextInput.create(Key.RENAME + ':' + emoji.getUrl().hashCode(),
                            str(Key.RENAME_SPECIFIC).formatted(emoji.getName()),
                            TextInputStyle.SHORT).setValue(emoji.getName()).build());

                    // singular mappings
                else modal.addActionRow(TextInput.create(Key.COMMAND_EMOJI_IMPORT_RENAME_MULTI_DESC,
                                str(Key.COMMAND_EMOJI_IMPORT_RENAME_MULTI_DESC),
                                TextInputStyle.PARAGRAPH)
                        .setValue(selected.stream()
                                .map(emoji -> emoji.getName() + '=')
                                .collect(Collectors.joining("\n")))
                        .build());
                event.replyModal(modal.build()).queue();
                break;
            case Key.COMMAND_EMOJI_IMPORT_CONFIRM_GUILD:
                var menu = StringSelectMenu.create(Key.COMMAND_EMOJI_IMPORT_CONFIRM_GUILD)
                        .setDefaultValues(String.valueOf(KannaApplication.KANNAS_CAVE_GUILD_ID));
                jda.getGuilds()
                        .stream()
                        .filter(guild -> guild.getSelfMember().hasPermission(Permission.CREATE_GUILD_EXPRESSIONS))
                        .forEach(guild -> menu.addOption(guild.getName(), guild.getId()));
                event.editMessage(new MessageEditBuilder().setEmbeds()
                        .setContent(str(Key.COMMAND_EMOJI_IMPORT_CONFIRM_GUILD_TITLE))
                        .setActionRow(menu.setMaxValues(1).build())
                        .build()).queue();

                break;
            case Key.COMMAND_EMOJI_IMPORT_CONFIRM_APP:
                applyChanges(null, event);
                break;
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!Key.RENAME.equals(event.getModalId())) return;

        var changed = new HashSet<RegisteredEmoji>();
        var values  = event.getValues();
        if (values.stream().anyMatch(mapping -> mapping.getId().startsWith(Key.RENAME))) {
            // singular mappings
            for (var mapping : values) {
                var key = Integer.parseInt(mapping.getId().substring(Key.RENAME.length() + 1));
                var emoji = selected.stream()
                        .filter(reg -> reg.getUrl().hashCode() == key)
                        .findAny()
                        .orElseThrow(() -> new NoSuchElementException("Could not map key '%s' to any selected emoji".formatted(
                                key)));

                emoji.setName(mapping.getAsString());
                changed.add(emoji);
            }
        } else {
            // mapping list
            record Mapping(String oldName, String newName) {}

            var count = Arrays.stream(values.stream()
                            .findAny()
                            .filter(mapping -> Key.COMMAND_EMOJI_IMPORT_RENAME_MULTI_DESC.equals(mapping.getId()))
                            .orElseThrow(() -> new NoSuchElementException("No multi-mappings input found"))
                            .getAsString()
                            .split("\r?\n"))
                    .flatMap(line -> {
                        var split = line.trim().split("=");
                        return split.length < 2 || split[1].isBlank()
                               ? Stream.empty()
                               : Stream.of(new Mapping(split[0].trim(), split[1].trim()));
                    })
                    .flatMap(mapping -> selected.stream()
                            .filter(byName(mapping.oldName))
                            .map(emoji -> emoji.setName(mapping.newName))
                            .filter(changed::add))
                    .count();
            Log.at(Level.INFO, "Found %d mapping names".formatted(count));
        }

        var message = Objects.requireNonNull(event.getMessage(), "originalMessage");
        var row     = message.getActionRows().getFirst();
        var menu    = StringSelectMenu.create(Key.COMMAND_EMOJI_IMPORT_MENU);
        var embeds  = new ArrayList<MessageEmbed>();

        selected.stream().map(emoji -> {
            if (!emoji.isExported()) embeds.add(emoji.asEmbed());
            return emoji.asSelectOption();
        }).forEach(menu::addOptions);
        menu.setMaxValues(menu.getOptions().size());
        row.updateComponent(Key.COMMAND_EMOJI_IMPORT_MENU, menu.build());

        var edit = MessageEditBuilder.fromMessage(message).setEmbeds(embeds).setActionRow(row.getComponents()).build();
        event.editMessage(edit).queue();

        bean(EmojiRegistry.class).saveAll(changed.stream().filter(RegisteredEmoji::isExported).toList());
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        switch (event.getComponentId()) {
            case Key.COMMAND_EMOJI_IMPORT_MENU:
                event.getSelectedOptions()
                        .stream()
                        .flatMap(option -> importQueue.stream()
                                .filter(emoji -> String.valueOf(emoji.getUrl().hashCode()).equals(option.getValue())))
                        .forEach(selected::add);

                var msg = MessageEditBuilder.fromMessage(event.getMessage());
                var remove = new ArrayList<MessageEmbed>();
                var embeds = msg.getEmbeds();
                for (var embed : embeds)
                    if (selected.stream()
                            .map(RegisteredEmoji::getName)
                            .noneMatch(Objects.requireNonNull(embed.getTitle())::equals)) remove.add(embed);
                msg.setEmbeds(embeds.stream().filter(Predicate.not(remove::contains)).toList());

                event.editMessage(msg.setContent(str(Key.COMMAND_EMOJI_IMPORT_RENAME_ASK_TITLE))
                        .setActionRow(Button.secondary(Key.RENAME, str(Key.RENAME)),
                                Button.success(Key.COMMAND_EMOJI_IMPORT_CONFIRM_GUILD,
                                        str(Key.COMMAND_EMOJI_IMPORT_CONFIRM_GUILD))
                                //,Button.success(Key.COMMAND_EMOJI_IMPORT_CONFIRM_APP, str(Key.COMMAND_EMOJI_IMPORT_CONFIRM_APP))
                        )
                        .build()).queue();
                break;
            case Key.COMMAND_EMOJI_IMPORT_CONFIRM_GUILD:
                applyChanges(jda.getGuildById(event.getValues().getFirst()), event);
                break;
        }
    }

    @Override
    public void clear() {
        importQueue.clear();
        selected.clear();
    }

    private void applyChanges(Guild guild, ComponentInteraction event) {
        if (selected.stream().allMatch(RegisteredEmoji::isExported)) {
            event.editMessage(str(Key.COMMAND_EMOJI_IMPORT_FAIL_NO_EXPORTS)).queue();
            return;
        }

        event.deferEdit().flatMap(hook -> {
            var importTasks = new ArrayList<RestAction<?>>();
            var registry    = bean(EmojiRegistry.class);
            var imported = selected.stream().peek(emoji -> {
                if (!emoji.isExported()) importTasks.add(emoji.export(guild).map(appEmoji -> {
                    emoji.setUrl(appEmoji.getImageUrl());
                    return emoji.setEmojiId(appEmoji.getIdLong());
                }).map(registry::save));
            }).collect(Collectors.toUnmodifiableSet());

            RestAction.allOf(importTasks).submit().join();
            bean(EmojiRegistry.class).saveAll(imported);

            clear();

            var str = new StringBuilder();
            for (var emoji : imported)
                str.append("\n# %s - %s".formatted(emoji.asCustomEmoji().orElseThrow().getAsMention(),
                        emoji.getName()));
            return hook.editOriginal(MessageEditBuilder.fromMessage(event.getMessage())
                    .setContent(str(Key.COMMAND_EMOJI_IMPORT_SUCCESS).formatted(imported.size()) + '\n' + str)
                    .setEmbeds(Collections.emptyList())
                    .setComponents()
                    .build());
        }).queue();
    }

    private interface Key {
        String RENAME                                   = "generic.title.rename";
        String RENAME_SPECIFIC                          = "generic.title.rename.f.name";
        String APPLY                                    = "generic.title.apply";
        String COMMAND_EMOJI_IMPORT_NAME                = "command.emoji.import.name";
        String COMMAND_EMOJI_IMPORT_MENU                = "command.emoji.import.menu";
        String COMMAND_EMOJI_IMPORT_MENU_TITLE          = "command.emoji.import.menu.title";
        String COMMAND_EMOJI_IMPORT_RENAME_ASK_TITLE    = "command.emoji.import.rename.ask.title";
        String COMMAND_EMOJI_IMPORT_RENAME_TITLE        = "command.emoji.import.rename.title";
        String COMMAND_EMOJI_IMPORT_RENAME_MULTI_DESC   = "command.emoji.import.rename.multi.desc";
        String COMMAND_EMOJI_IMPORT_CONFIRM_APP         = "command.emoji.import.confirm.app";
        String COMMAND_EMOJI_IMPORT_CONFIRM_GUILD       = "command.emoji.import.confirm.guild";
        String COMMAND_EMOJI_IMPORT_CONFIRM_GUILD_TITLE = "command.emoji.import.confirm.guild.title";
        String COMMAND_EMOJI_IMPORT_SUCCESS             = "command.emoji.import.success.f.amount";
        String COMMAND_EMOJI_IMPORT_FAIL_NO_EXPORTS     = "command.emoji.import.fail.no_exports";
    }
}
