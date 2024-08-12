package de.kneitzel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/**
 * Simple Greeting class for some demonstration.
 */
public class Greeting {

    /**
     * Default name that should be greeted if no name is given.
     */
    public static final String DEFAULT_NAME = "Welt";

    /**
     * Name that should be greeted
     * <p>
     *     Cannot be null.
     * </p>
     */
    @NotNull
    private final String name;

    /**
     * Gets the name that should be greeted.
     * @return Name that should be greeted.
     */
    public @NotNull String getName() {
        return name;
    }

    /**
     * Creates a new Instance of Greeting which greets the "World".
     */
    public Greeting() {
        this(null);
    }

    /**
     * Creates a new instance of Greeting to greet the given name.
     * @param name Name that should be greeted by Greeting.
     */
    public Greeting(@Nullable final String name) {
        this.name = name == null ? DEFAULT_NAME : name;
    }

    /**
     * String representation of this instance.
     * @return String representation of "Hello name!"
     */
    @NotNull
    @Override
    public String toString() {
        return "Hallo " + name + "!";
    }
}
