package de.kaleidox.kanna;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.comroid.api.data.seri.DataNode;

@Data
@NoArgsConstructor
public class BotConfig implements DataNode {
    public final Database database = new Database();
    public String token;

    @Data
    @NoArgsConstructor
    public static class Database {
        public String uri;
        public String username;
        public String password;
    }
}
