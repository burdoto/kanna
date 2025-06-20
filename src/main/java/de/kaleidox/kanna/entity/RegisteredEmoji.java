package de.kaleidox.kanna.entity;

import de.kaleidox.kanna.repo.EmojiRegistry;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.requests.RestAction;
import org.comroid.annotations.Doc;
import org.comroid.api.Polyfill;
import org.comroid.api.attr.Named;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static de.kaleidox.kanna.util.ApplicationContextProvider.*;

@Data
@Entity
public class RegisteredEmoji implements Named {
    public static RegisteredEmoji of(CustomEmoji emoji) {
        var registry = bean(EmojiRegistry.class);
        return registry.findById(emoji.getIdLong())
                .orElseGet(() -> registry.save(new RegisteredEmoji(emoji.getIdLong(),
                        emoji.getName(),
                        emoji.getImageUrl(),
                        emoji.isAnimated())));
    }

    public static RegisteredEmoji of(Message.Attachment attachment) {
        var fileName = attachment.getFileName();
        return new RegisteredEmoji(fileName.substring(0, fileName.lastIndexOf('.')), attachment.getUrl());
    }

    public static RegisteredEmoji of(StickerItem stickerItem) {
        return new RegisteredEmoji(stickerItem.getName(), stickerItem.getIconUrl());
    }

    @Id long emojiId;
    String name, url;
    boolean animated;
    long    importedBy, importedFrom;
    @ElementCollection @MapKeyColumn(name = "target") @Column(name = "resultId")
    @CollectionTable(joinColumns = @JoinColumn(name = "emojiId"))
    Map<@Doc("target") @NotNull Long, @Doc("resultId") @NotNull Long> exportedTo;

    protected RegisteredEmoji() {
    }

    private RegisteredEmoji(String name, String url) {
        this(0L, name, url, false);
    }

    private RegisteredEmoji(long emojiId, String name, String url, boolean animated) {
        this(emojiId, name, url, 0L, 0L, animated);
    }

    private RegisteredEmoji(
            long emojiId, String name, String url, long importedBy, long importedFrom, boolean animated) {
        this(emojiId, name, url, importedBy, importedFrom, animated, new HashMap<>());
    }

    private RegisteredEmoji(
            long emojiId, String name, String url, long importedBy, long importedFrom, boolean animated,
            Map<@Doc("target") @NotNull Long, @Doc("resultId") @NotNull Long> exportedTo
    ) {
        this.emojiId      = emojiId;
        this.name         = name;
        this.url          = url;
        this.importedBy   = importedBy;
        this.importedFrom = importedFrom;
        this.animated     = animated;
        this.exportedTo   = exportedTo;
    }

    public boolean isExported() {
        return emojiId != 0;
    }

    public Optional<CustomEmoji> asCustomEmoji() {
        return Optional.of(Emoji.fromCustom(name, emojiId, animated)).filter($ -> isExported());
    }

    public SelectOption asSelectOption() {
        var option = SelectOption.of(name, String.valueOf(url.hashCode()));
        if (isExported()) option = option.withEmoji(asCustomEmoji().orElse(null));
        return option;
    }

    public MessageEmbed asEmbed() {
        return new EmbedBuilder().setTitle(name).setImage(url).build();
    }

    public RestAction<? extends CustomEmoji> export() {
        return export(null);
    }

    public RestAction<? extends CustomEmoji> export(@Nullable Guild guild) {
        Icon icon;
        try (var is = Polyfill.url(url).openStream()) {
            icon = Icon.from(is);
        } catch (IOException e) {
            throw new RuntimeException("Could not read icon data from url " + url, e);
        }

        return guild != null ? guild.createEmoji(name, icon) : bean(JDA.class).createApplicationEmoji(name, icon);
    }
}
