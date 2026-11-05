package app.trailplan.devices;

/** The remote route linked to a saved route no longer exists (deleted in the maker's app). */
public class CloudRouteGoneException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CloudRouteGoneException(Throwable cause) {
    super("The route no longer exists in the cloud account.", cause);
  }
}
