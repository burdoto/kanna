package de.kaleidox.kanna.repo;

import de.kaleidox.kanna.entity.RegisteredEmoji;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EmojiRegistry extends CrudRepository<RegisteredEmoji, @NotNull Long> {
    List<RegisteredEmoji> findByName(@NotNull String name);
}
