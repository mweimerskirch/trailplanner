package app.trailplan.users;

/** The request needs a rider, but none is chosen (or the chosen one was deleted). */
public class NoUserSelectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NoUserSelectedException() {
    super("Choose a rider first.");
  }
}
