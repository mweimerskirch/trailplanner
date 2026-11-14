package app.trailplanner.users;

/**
 * The rider chosen in this browser (cookie {@value #COOKIE}). Declare a parameter of this type in a
 * controller method to require a chosen rider; requests without one get 401 with reason {@code
 * NO_USER}, and the UI then shows the rider selection.
 *
 * <p>Riders have no passwords: this separates libraries, it does not protect them.
 */
public record CurrentUser(long id, String name) {

  public static final String COOKIE = "trailplan_user";
}
