package org.opends.common.api.controls;

import static org.opends.server.util.ServerConstants.OID_AUTHZID_RESPONSE;
import static org.opends.server.util.ServerConstants.OID_AUTHZID_REQUEST;
import org.opends.server.util.Validator;
import org.opends.server.types.ByteString;
import org.opends.common.api.DN;
import org.opends.common.api.DecodeException;
import org.opends.messages.Message;
import static org.opends.messages.ProtocolMessages.ERR_AUTHZIDREQ_CONTROL_HAS_VALUE;
import static org.opends.messages.ProtocolMessages.ERR_AUTHZIDRESP_NO_CONTROL_VALUE;

import java.io.IOException;

/**
 * This class implements the authorization identity control as defined
 * in RFC 3829.
 */
public class AuthorizationIdentityControl
{
    /**
   * The Control Decoder that can be used to decode the request
   * control.
   */
  public static final ControlDecoder<Request>
      REQUEST_DECODER = new RequestDecoder();

  /**
   * The Control Decoder that can be used to decode the response
   * control.
   */
  public static final ControlDecoder<Response>
      RESPONSE_DECODER = new ResponseDecoder();

  /**
   * This class implements the authorization identity request control
   * as defined in RFC 3829.
   */
  public static class Request extends Control
  {
    public Request()
    {
      super(OID_AUTHZID_RESPONSE, false);
    }

    public Request(boolean isCritical)
    {
      super(OID_AUTHZID_RESPONSE, isCritical);
    }

    public ByteString getValue()
    {
      return null;
    }

    public boolean hasValue()
    {
      return false;
    }

    public void toString(StringBuilder buffer)
    {
      buffer.append("AuthorizationIdentityRequestControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(")");
    }
  }

  public static class Response extends Control
  {
    // The authorization ID for this control.
    private String authorizationID;



    /**
     * Creates a new authorization identity response control with the provided
     * information.
     *
     * @param  authorizationID  The authorization ID for this control.
     */
    public Response(String authorizationID)
    {
      this(false, authorizationID);
    }


    /**
     * Creates a new authorization identity response control with the provided
     * information.
     *
     * @param  isCritical  Indicates whether this control should be
     *                     considered critical in processing the
     *                     request.
     * @param  authorizationID  The authorization ID for this control.
     */
    public Response(boolean isCritical, String authorizationID)
    {
      super(OID_AUTHZID_RESPONSE, isCritical);

      Validator.ensureNotNull(authorizationID);
      this.authorizationID = authorizationID;
    }




    /**
     * Creates a new authorization identity response control with the provided
     * information.
     *
     * @param  authorizationDN  The authorization DN for this control.
     */
    public Response(DN authorizationDN)
    {
      this(false, authorizationDN);
    }

    /**
     * Creates a new authorization identity response control with the provided
     * information.
     *
     * @param  isCritical  Indicates whether this control should be
     *                     considered critical in processing the
     *                     request.
     * @param  authorizationDN  The authorization DN for this control.
     */
    public Response(boolean isCritical, DN authorizationDN)
    {
      super(OID_AUTHZID_REQUEST, isCritical);

      Validator.ensureNotNull(authorizationDN);
      if (authorizationDN == null)
      {
        this.authorizationID = "dn:";
      }
      else
      {
        this.authorizationID = "dn:" + authorizationDN.toString();
      }
    }

    public ByteString getValue() {
      return ByteString.valueOf(authorizationID);
    }

    public boolean hasValue() {
      return true;
    }


    /**
     * Retrieves the authorization ID for this authorization identity response
     * control.
     *
     * @return  The authorization ID for this authorization identity response
     *          control.
     */
    public String getAuthorizationID()
    {
      return authorizationID;
    }



    /**
     * Appends a string representation of this authorization identity response
     * control to the provided buffer.
     *
     * @param  buffer  The buffer to which the information should be appended.
     */
    public void toString(StringBuilder buffer)
    {
      buffer.append("AuthorizationIdentityResponseControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", authzID=\"");
      buffer.append(authorizationID);
      buffer.append("\")");
    }

  }


  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private static final class RequestDecoder
      implements ControlDecoder<Request>
  {
    /**
     * {@inheritDoc}
     */
    public Request decode(boolean isCritical, ByteString value)
        throws DecodeException
    {
      if (value != null)
      {
        Message message = ERR_AUTHZIDREQ_CONTROL_HAS_VALUE.get();
        throw new DecodeException(message);
      }


      return new Request(isCritical);
    }

    public String getOID()
    {
      return OID_AUTHZID_REQUEST;
    }
  }

  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class ResponseDecoder
      implements ControlDecoder<Response>
  {
    /**
     * {@inheritDoc}
     */
    public Response decode(boolean isCritical, ByteString value)
        throws DecodeException
    {
      if (value == null)
      {
        Message message = ERR_AUTHZIDRESP_NO_CONTROL_VALUE.get();
        throw new DecodeException(message);
      }

      String authID = value.toString();
      return new Response(isCritical, authID);

    }

    public String getOID()
    {
      return OID_AUTHZID_RESPONSE;
    }
  }
}
