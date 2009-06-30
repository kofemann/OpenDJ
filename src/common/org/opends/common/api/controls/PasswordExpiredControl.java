package org.opends.common.api.controls;

import org.opends.server.types.ByteString;
import static org.opends.server.util.ServerConstants.OID_NS_PASSWORD_EXPIRED;
import org.opends.messages.Message;
import static org.opends.messages.ProtocolMessages.ERR_PWEXPIRED_CONTROL_INVALID_VALUE;
import org.opends.common.api.DecodeException;

import java.io.IOException;

/**
 * This class implements the Netscape password expired control.  The value for
 * this control should be a string that indicates the length of time until the
 * password expires, but because it is already expired it will always be "0".
 */
public class PasswordExpiredControl extends Control
{
  private final static ByteString CONTROL_VALUE =
      ByteString.valueOf("0");
    /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<PasswordExpiredControl>
  {
    /**
     * {@inheritDoc}
     */
    public PasswordExpiredControl decode(boolean isCritical,
                                         ByteString value)
        throws DecodeException
    {
      if (value != null)
      {
        try
        {
          Integer.parseInt(value.toString());
        }
        catch (Exception e)
        {
          Message message = ERR_PWEXPIRED_CONTROL_INVALID_VALUE.get();
          throw new DecodeException(message);
        }
      }

      return new PasswordExpiredControl(isCritical);
    }

    public String getOID()
    {
      return OID_NS_PASSWORD_EXPIRED;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<PasswordExpiredControl> DECODER =
    new Decoder();

  /**
   * Creates a new instance of the password expired control with the default
   * settings.
   */
  public PasswordExpiredControl()
  {
    this(false);
  }

  /**
   * Creates a new instance of the password expired control with the provided
   * information.
   *
   * @param  isCritical  Indicates whether support for this control should be
   *                     considered a critical part of the client processing.
   */
  public PasswordExpiredControl(boolean isCritical)
  {
    super(OID_NS_PASSWORD_EXPIRED, isCritical);
  }

  public ByteString getValue() {
    return CONTROL_VALUE;
  }

  public boolean hasValue() {
    return true;
  }



  /**
   * Appends a string representation of this password expired control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PasswordExpiredControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(")");
  }
}
