package de.kaleidox.kanna.repo;

import de.kaleidox.kanna.entity.RegisteredEmoji;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;

import java.util.stream.Stream;

public interface EmojiRegistry extends CrudRepository<RegisteredEmoji, @NotNull Long> {
    Stream<RegisteredEmoji> getByName(@NotNull String name);
}
