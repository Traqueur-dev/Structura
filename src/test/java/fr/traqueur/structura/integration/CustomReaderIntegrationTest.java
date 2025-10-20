package fr.traqueur.structura.integration;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Custom Reader Integration Tests")
class CustomReaderIntegrationTest {

    // Mock Adventure API classes for testing
    interface Component {
        String asPlainText();
    }

    static class TextComponent implements Component {
        private final String text;
        private final String color;

        TextComponent(String text, String color) {
            this.text = text;
            this.color = color;
        }

        @Override
        public String asPlainText() {
            return text;
        }

        public String getColor() {
            return color;
        }

        @Override
        public String toString() {
            return "TextComponent{text='" + text + "', color='" + color + "'}";
        }
    }

    static class MiniMessage {
        private static final MiniMessage INSTANCE = new MiniMessage();

        static MiniMessage miniMessage() {
            return INSTANCE;
        }

        Component deserialize(String input) {
            // Simple parsing: <color>text</color>
            if (input.contains("<green>")) {
                String text = input.replace("<green>", "").replace("</green>", "");
                return new TextComponent(text, "green");
            } else if (input.contains("<red>")) {
                String text = input.replace("<red>", "").replace("</red>", "");
                return new TextComponent(text, "red");
            } else if (input.contains("<blue>")) {
                String text = input.replace("<blue>", "").replace("</blue>", "");
                return new TextComponent(text, "blue");
            } else {
                return new TextComponent(input, "white");
            }
        }
    }

    // Test configuration records
    public record MessageConfig(
            Component welcomeMessage,
            Component errorMessage
    ) implements Loadable {}

    public record ChatConfig(
            Component prefix,
            Component suffix,
            String playerName
    ) implements Loadable {}

    public record MessagesConfig(
            List<Component> announcements,
            Map<String, Component> customMessages
    ) implements Loadable {}

    public record SimpleConfig(String message) implements Loadable {}

    public record ServerConfig(
            String serverName,
            int maxPlayers,
            Component motd,
            Component shutdownMessage,
            List<Component> rules
    ) implements Loadable {}

    public record ChatFormat(
            Component prefix,
            Component suffix
    ) implements Loadable {}

    public record PlayerConfig(
            String playerName,
            ChatFormat chatFormat
    ) implements Loadable {}

    @BeforeEach
    void setUp() {
        CustomReaderRegistry.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        CustomReaderRegistry.getInstance().clear();
    }

    @Nested
    @DisplayName("Basic Component Conversion")
    class BasicComponentConversionTest {

        @Test
        @DisplayName("Should convert simple YAML strings to Components")
        void shouldConvertSimpleStringsToComponents() {
            // Register the Component reader
            CustomReaderRegistry.getInstance().register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            String yaml = """
                    welcome-message: "<green>Welcome to the server!</green>"
                    error-message: "<red>An error occurred!</red>"
                    """;

            MessageConfig config = Structura.parse(yaml, MessageConfig.class);

            assertNotNull(config.welcomeMessage());
            assertNotNull(config.errorMessage());
            assertEquals("Welcome to the server!", config.welcomeMessage().asPlainText());
            assertEquals("An error occurred!", config.errorMessage().asPlainText());

            assertTrue(config.welcomeMessage() instanceof TextComponent);
            assertTrue(config.errorMessage() instanceof TextComponent);

            TextComponent welcomeComponent = (TextComponent) config.welcomeMessage();
            TextComponent errorComponent = (TextComponent) config.errorMessage();

            assertEquals("green", welcomeComponent.getColor());
            assertEquals("red", errorComponent.getColor());
        }

        @Test
        @DisplayName("Should handle components with mixed content")
        void shouldHandleComponentsWithMixedContent() {
            CustomReaderRegistry.getInstance().register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            String yaml = """
                    prefix: "<blue>[Server]</blue>"
                    suffix: "<green>Online</green>"
                    player-name: "Player123"
                    """;

            ChatConfig config = Structura.parse(yaml, ChatConfig.class);

            assertNotNull(config.prefix());
            assertNotNull(config.suffix());
            assertEquals("Player123", config.playerName());

            assertEquals("[Server]", config.prefix().asPlainText());
            assertEquals("Online", config.suffix().asPlainText());

            TextComponent prefixComponent = (TextComponent) config.prefix();
            TextComponent suffixComponent = (TextComponent) config.suffix();

            assertEquals("blue", prefixComponent.getColor());
            assertEquals("green", suffixComponent.getColor());
        }

        @Test
        @DisplayName("Should handle plain text without formatting")
        void shouldHandlePlainTextWithoutFormatting() {
            CustomReaderRegistry.getInstance().register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            String yaml = """
                    welcome-message: "Simple welcome message"
                    error-message: "Simple error message"
                    """;

            MessageConfig config = Structura.parse(yaml, MessageConfig.class);

            assertEquals("Simple welcome message", config.welcomeMessage().asPlainText());
            assertEquals("Simple error message", config.errorMessage().asPlainText());

            TextComponent welcomeComponent = (TextComponent) config.welcomeMessage();
            assertEquals("white", welcomeComponent.getColor());
        }
    }

    @Nested
    @DisplayName("Component Collections")
    class ComponentCollectionsTest {

        @Test
        @DisplayName("Should convert lists of components")
        void shouldConvertListsOfComponents() {
            CustomReaderRegistry.getInstance().register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            String yaml = """
                    announcements:
                      - "<green>Server is starting</green>"
                      - "<blue>Loading plugins</blue>"
                      - "<green>Server started successfully</green>"
                    custom-messages: {}
                    """;

            MessagesConfig config = Structura.parse(yaml, MessagesConfig.class);

            assertNotNull(config.announcements());
            assertEquals(3, config.announcements().size());

            assertEquals("Server is starting", config.announcements().get(0).asPlainText());
            assertEquals("Loading plugins", config.announcements().get(1).asPlainText());
            assertEquals("Server started successfully", config.announcements().get(2).asPlainText());

            TextComponent first = (TextComponent) config.announcements().get(0);
            TextComponent second = (TextComponent) config.announcements().get(1);

            assertEquals("green", first.getColor());
            assertEquals("blue", second.getColor());
        }

        @Test
        @DisplayName("Should convert maps of components")
        void shouldConvertMapsOfComponents() {
            CustomReaderRegistry.getInstance().register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            String yaml = """
                    announcements: []
                    custom-messages:
                      join: "<green>Player joined!</green>"
                      leave: "<red>Player left!</red>"
                      death: "<red>Player died!</red>"
                    """;

            MessagesConfig config = Structura.parse(yaml, MessagesConfig.class);

            assertNotNull(config.customMessages());
            assertEquals(3, config.customMessages().size());

            assertTrue(config.customMessages().containsKey("join"));
            assertTrue(config.customMessages().containsKey("leave"));
            assertTrue(config.customMessages().containsKey("death"));

            assertEquals("Player joined!", config.customMessages().get("join").asPlainText());
            assertEquals("Player left!", config.customMessages().get("leave").asPlainText());
            assertEquals("Player died!", config.customMessages().get("death").asPlainText());

            TextComponent joinComponent = (TextComponent) config.customMessages().get("join");
            TextComponent leaveComponent = (TextComponent) config.customMessages().get("leave");

            assertEquals("green", joinComponent.getColor());
            assertEquals("red", leaveComponent.getColor());
        }
    }

    @Nested
    @DisplayName("Reader Lifecycle")
    class ReaderLifecycleTest {

        @Test
        @DisplayName("Should work without custom reader for normal types")
        void shouldWorkWithoutCustomReaderForNormalTypes() {
            // No reader registered for Component
            // But we have a normal String field that should work

            String yaml = """
                    message: "Hello World"
                    """;

            SimpleConfig config = Structura.parse(yaml, SimpleConfig.class);

            assertEquals("Hello World", config.message());
        }

        @Test
        @DisplayName("Should allow unregistering readers")
        void shouldAllowUnregisteringReaders() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            registry.register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            assertTrue(registry.hasReader(Component.class));

            registry.unregister(Component.class);

            assertFalse(registry.hasReader(Component.class));
        }

        @Test
        @DisplayName("Should maintain reader across multiple parsing operations")
        void shouldMaintainReaderAcrossMultipleParsings() {
            CustomReaderRegistry.getInstance().register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            String yaml1 = """
                    welcome-message: "<green>Welcome 1</green>"
                    error-message: "<red>Error 1</red>"
                    """;

            String yaml2 = """
                    welcome-message: "<green>Welcome 2</green>"
                    error-message: "<red>Error 2</red>"
                    """;

            MessageConfig config1 = Structura.parse(yaml1, MessageConfig.class);
            MessageConfig config2 = Structura.parse(yaml2, MessageConfig.class);

            assertEquals("Welcome 1", config1.welcomeMessage().asPlainText());
            assertEquals("Welcome 2", config2.welcomeMessage().asPlainText());
        }
    }

    @Nested
    @DisplayName("End-to-End Workflows")
    class EndToEndWorkflowsTest {

        @Test
        @DisplayName("Should handle complete server configuration with components")
        void shouldHandleCompleteServerConfiguration() {
            CustomReaderRegistry.getInstance().register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            String yaml = """
                    server-name: "MyServer"
                    max-players: 100
                    motd: "<green>Welcome to MyServer!</green>"
                    shutdown-message: "<red>Server is shutting down...</red>"
                    rules:
                      - "<blue>Rule 1: Be respectful</blue>"
                      - "<blue>Rule 2: No griefing</blue>"
                      - "<blue>Rule 3: Have fun!</blue>"
                    """;

            ServerConfig config = Structura.parse(yaml, ServerConfig.class);

            assertEquals("MyServer", config.serverName());
            assertEquals(100, config.maxPlayers());
            assertEquals("Welcome to MyServer!", config.motd().asPlainText());
            assertEquals("Server is shutting down...", config.shutdownMessage().asPlainText());
            assertEquals(3, config.rules().size());

            TextComponent motd = (TextComponent) config.motd();
            assertEquals("green", motd.getColor());

            for (Component rule : config.rules()) {
                TextComponent ruleComponent = (TextComponent) rule;
                assertEquals("blue", ruleComponent.getColor());
            }
        }

        @Test
        @DisplayName("Should integrate with nested records containing components")
        void shouldIntegrateWithNestedRecordsContainingComponents() {
            CustomReaderRegistry.getInstance().register(
                    Component.class,
                    str -> MiniMessage.miniMessage().deserialize(str)
            );

            String yaml = """
                    player-name: "TestPlayer"
                    chat-format:
                      prefix: "<green>[VIP]</green>"
                      suffix: "<blue>[Online]</blue>"
                    """;

            PlayerConfig config = Structura.parse(yaml, PlayerConfig.class);

            assertEquals("TestPlayer", config.playerName());
            assertNotNull(config.chatFormat());
            assertEquals("[VIP]", config.chatFormat().prefix().asPlainText());
            assertEquals("[Online]", config.chatFormat().suffix().asPlainText());

            TextComponent prefix = (TextComponent) config.chatFormat().prefix();
            TextComponent suffix = (TextComponent) config.chatFormat().suffix();

            assertEquals("green", prefix.getColor());
            assertEquals("blue", suffix.getColor());
        }
    }
}