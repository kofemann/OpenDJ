package org.opends.common.api.controls;

import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.ServerConstants.OID_NS_PASSWORD_EXPIRING;
import org.opends.messages.Message;
import static org.opends.messages.ProtocolMessages.ERR_PWEXPIRING_NO_CONTROL_VALUE;
import static org.opends.messages.ProtocolMessages.ERR_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION;
import org.opends.common.api.DecodeException;

import java.io.IOException;

/**
 * This class implements the Netscape password expiring control, which serves as
 * a warning to clients that the user's password is about to expire. The only
 * element contained in the control value is a string representation of the
 * number of seconds until expiration.
 */
public class PasswordExpiringControl extends Control
{
    /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<PasswordExpiringControl>
  {
    /**
     * {@inheritDoc}
     */
    public PasswordExpiringControl decode(boolean isCritical,
                                          ByteString value)
        throws DecodeException
    {
      if (value == null)
      {
        Message message = ERR_PWEXPIRING_NO_CONTROL_VALUE.get();
        throw new DecodeException(message);
      }

      int secondsUntilExpiration;
      try
      {
        secondsUntilExpiration =
            Integer.parseInt(value.toString());
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION.
            get(getExceptionMessage(e));
        throw new DecodeException(message);
      }


      return new PasswordExpiringControl(isCritical,
          secondsUntilExpiration);
    }

    public String getOID()
    {
      return OID_NS_PASSWORD_EXPIRING;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<PasswordExpiringControl> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The length of time in seconds until the password actually expires.
  private int secondsUntilExpiration;



  /**
   * Creates a new instance of the password expiring control with the provided
   * information.
   *
   * @param  secondsUntilExpiration  The length of time in seconds until the
   *                                 password actually expires.
   */
  public PasswordExpiringControl(int secondsUntilExpiration)
  {
    this(false, secondsUntilExpiration);
  }



  /**
   * Creates a new instance of the password expiring control with the provided
   * information.
   *
   * @param  isCritical              Indicates whether support for this control
   *                                 should be considered a critical part of the
   *                                 client processing.
   * @param  secondsUntilExpiration  The length of time in seconds until the
   *                                 password actually expires.
   */
  public PasswordExpiringControl(boolean isCritical, int secondsUntilExpiration)
  {
    super(OID_NS_PASSWORD_EXPIRING, isCritical);


    this.secondsUntilExpiration = secondsUntilExpiration;
  }

  public ByteString getValue() {
    return ByteString.valueOf(String.valueOf(secondsUntilExpiration));
  }

  public boolean hasValue() {
    return true;
  }



  /**
   * Retrieves the length of time in seconds until the password actually
   * expires.
   *
   * @return  The length of time in seconds until the password actually expires.
   */
  public int getSecondsUntilExpiration()
  {
    return secondsUntilExpiration;
  }



  /**
   * Appends a string representation of this password expiring control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PasswordExpiringControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(", secondsUntilExpiration=");
    buffer.append(secondsUntilExpiration);
    buffer.append(")");
  }
}
