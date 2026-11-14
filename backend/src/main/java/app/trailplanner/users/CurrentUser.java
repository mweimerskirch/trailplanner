package app.trailplanner.users;

/**
 * The rider chosen in this browser (cookie {@value #COOKIE}). Declare a parameter of this type in a
 * controller method to require a chosen rider; requests without one get 401 with reason {@code
 * NO_USER}, and the UI then shows the rider selection.
 *
 * <p>Riders have no passwords: this separates libraries, it does not protect them.
 *
 * <p>{@code device} is the id of the rider's {@code DeviceIntegration}, or null for files only.
 */
public record CurrentUser(long id, String name, String device) {

  public static final String COOKIE = "trailplan_user";
}
