/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.protocols;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.LDIFConnectionHandlerCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DN;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.HostPort;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Operation;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.ChangeRecordEntry;
import org.opends.server.util.DeleteChangeRecordEntry;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ModifyChangeRecordEntry;
import org.opends.server.util.ModifyDNChangeRecordEntry;
import org.opends.server.util.TimeThread;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines an LDIF connection handler, which can be used to watch for
 * new LDIF files to be placed in a specified directory.  If a new LDIF file is
 * detected, the connection handler will process any changes contained in that
 * file as internal operations.
 */
public final class LDIFConnectionHandler
       extends ConnectionHandler<LDIFConnectionHandlerCfg>
       implements ConfigurationChangeListener<LDIFConnectionHandlerCfg>,
                  AlertGenerator
{
  /** The debug log tracer for this class. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /** Indicates whether this connection handler is currently stopped. */
  private volatile boolean isStopped;

  /** Indicates whether we should stop this connection handler. */
  private volatile boolean stopRequested;

  /** The path to the directory to watch for new LDIF files. */
  private File ldifDirectory;

  /** The internal client connection that will be used for all processing. */
  private InternalClientConnection conn;

  /** The current configuration for this LDIF connection handler. */
  private LDIFConnectionHandlerCfg currentConfig;

  /** The thread used to run the connection handler. */
  private Thread connectionHandlerThread;

  /** Help to not warn permanently and fullfill the log file in debug mode. */
  private boolean alreadyWarn;


  /**
   * Creates a new instance of this connection handler.  All initialization
   * should be performed in the {@code initializeConnectionHandler} method.
   */
  public LDIFConnectionHandler()
  {
    super("LDIFConnectionHandler");

    isStopped               = true;
    stopRequested           = false;
    connectionHandlerThread = null;
    alreadyWarn = false;
  }



  /** {@inheritDoc} */
  @Override
  public void initializeConnectionHandler(ServerContext serverContext, LDIFConnectionHandlerCfg
                                               configuration)
  {
    String ldifDirectoryPath = configuration.getLDIFDirectory();
    ldifDirectory = new File(ldifDirectoryPath);

    // If we have a relative path to the instance, get the absolute one.
    if ( ! ldifDirectory.isAbsolute() ) {
      ldifDirectory = new File(DirectoryServer.getInstanceRoot()
          + File.separator + ldifDirectoryPath);
    }

    if (ldifDirectory.exists())
    {
      if (! ldifDirectory.isDirectory())
      {
        // The path specified as the LDIF directory exists, but isn't a
        // directory.  This is probably a mistake, and we should at least log
        // a warning message.
        logger.warn(WARN_LDIF_CONNHANDLER_LDIF_DIRECTORY_NOT_DIRECTORY,
            ldifDirectory.getAbsolutePath(), configuration.dn());
      }
    }
    else
    {
      // The path specified as the LDIF directory doesn't exist.  We should log
      // a warning message saying that we won't do anything until it's created.
      logger.warn(WARN_LDIF_CONNHANDLER_LDIF_DIRECTORY_MISSING,
          ldifDirectory.getAbsolutePath(), configuration.dn());
    }

    this.currentConfig = configuration;
    currentConfig.addLDIFChangeListener(this);
    DirectoryConfig.registerAlertGenerator(this);
    conn = InternalClientConnection.getRootConnection();
  }



  /** {@inheritDoc} */
  @Override
  public void finalizeConnectionHandler(LocalizableMessage finalizeReason)
  {
    stopRequested = true;

    for (int i=0; i < 5; i++)
    {
      if (isStopped)
      {
        return;
      }
      else
      {
        try
        {
          if (connectionHandlerThread != null && connectionHandlerThread.isAlive())
          {
            connectionHandlerThread.join(100);
            connectionHandlerThread.interrupt();
          }
          else
          {
            return;
          }
        } catch (Exception e) {}
      }
    }
  }



  /** {@inheritDoc} */
  @Override
  public String getConnectionHandlerName()
  {
    return "LDIF Connection Handler";
  }



  /** {@inheritDoc} */
  @Override
  public String getProtocol()
  {
    return "LDIF";
  }



  /** {@inheritDoc} */
  @Override
  public Collection<HostPort> getListeners()
  {
    // There are no listeners for this connection handler.
    return Collections.<HostPort>emptySet();
  }



  /** {@inheritDoc} */
  @Override
  public Collection<ClientConnection> getClientConnections()
  {
    // There are no client connections for this connection handler.
    return Collections.<ClientConnection>emptySet();
  }



  /** {@inheritDoc} */
  @Override
  public void run()
  {
    isStopped = false;
    connectionHandlerThread = Thread.currentThread();

    try
    {
      while (! stopRequested)
      {
        try
        {
          long startTime = System.currentTimeMillis();

          File dir = ldifDirectory;
          if (dir.exists() && dir.isDirectory())
          {
            File[] ldifFiles = dir.listFiles();
            if (ldifFiles != null)
            {
              for (File f : ldifFiles)
              {
                if (f.getName().endsWith(".ldif"))
                {
                  processLDIFFile(f);
                }
              }
            }
          }
          else
          {
            if (!alreadyWarn && logger.isTraceEnabled())
            {
              logger.trace("LDIF connection handler directory " +
                               dir.getAbsolutePath() +
                               " doesn't exist or isn't a directory");
              alreadyWarn = true;
            }
          }

          if (! stopRequested)
          {
            long currentTime = System.currentTimeMillis();
            long sleepTime   = startTime + currentConfig.getPollInterval() -
                               currentTime;
            if (sleepTime > 0)
            {
              try
              {
                Thread.sleep(sleepTime);
              }
              catch (InterruptedException ie)
              {
                logger.traceException(ie);
              }
            }
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
    }
    finally
    {
      connectionHandlerThread = null;
      isStopped = true;
    }
  }



  /**
   * Processes the contents of the provided LDIF file.
   *
   * @param  ldifFile  The LDIF file to be processed.
   */
  private void processLDIFFile(File ldifFile)
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("Beginning processing on LDIF file " +
                       ldifFile.getAbsolutePath());
    }

    boolean fullyProcessed = false;
    boolean errorEncountered = false;
    String inputPath = ldifFile.getAbsolutePath();

    LDIFImportConfig importConfig =
         new LDIFImportConfig(inputPath);
    importConfig.setInvokeImportPlugins(false);
    importConfig.setValidateSchema(true);

    String outputPath = inputPath + ".applied." + TimeThread.getGMTTime();
    if (new File(outputPath).exists())
    {
      int i=2;
      while (true)
      {
        if (! new File(outputPath + "." + i).exists())
        {
          outputPath = outputPath + "." + i;
          break;
        }

        i++;
      }
    }

    LDIFExportConfig exportConfig =
         new LDIFExportConfig(outputPath, ExistingFileBehavior.APPEND);
    if (logger.isTraceEnabled())
    {
      logger.trace("Creating applied file " + outputPath);
    }


    LDIFReader reader = null;
    LDIFWriter writer = null;

    try
    {
      reader = new LDIFReader(importConfig);
      writer = new LDIFWriter(exportConfig);

      while (true)
      {
        ChangeRecordEntry changeRecord;
        try
        {
          changeRecord = reader.readChangeRecord(false);
          if (logger.isTraceEnabled())
          {
            logger.trace("Read change record entry %s", changeRecord);
          }
        }
        catch (LDIFException le)
        {
          logger.traceException(le);

          errorEncountered = true;
          if (le.canContinueReading())
          {
            LocalizableMessage m =
                 ERR_LDIF_CONNHANDLER_CANNOT_READ_CHANGE_RECORD_NONFATAL.get(
                      le.getMessageObject());
            writer.writeComment(m, 78);
            continue;
          }
          else
          {
            LocalizableMessage m =
                 ERR_LDIF_CONNHANDLER_CANNOT_READ_CHANGE_RECORD_FATAL.get(
                      le.getMessageObject());
            writer.writeComment(m, 78);
            DirectoryConfig.sendAlertNotification(this,
                                 ALERT_TYPE_LDIF_CONNHANDLER_PARSE_ERROR, m);
            break;
          }
        }

        Operation operation = null;
        if (changeRecord == null)
        {
          fullyProcessed = true;
          break;
        }

        if (changeRecord instanceof AddChangeRecordEntry)
        {
          operation = conn.processAdd((AddChangeRecordEntry) changeRecord);
        }
        else if (changeRecord instanceof DeleteChangeRecordEntry)
        {
          operation = conn.processDelete(
               (DeleteChangeRecordEntry) changeRecord);
        }
        else if (changeRecord instanceof ModifyChangeRecordEntry)
        {
          operation = conn.processModify(
               (ModifyChangeRecordEntry) changeRecord);
        }
        else if (changeRecord instanceof ModifyDNChangeRecordEntry)
        {
          operation = conn.processModifyDN(
               (ModifyDNChangeRecordEntry) changeRecord);
        }

        if (operation == null)
        {
          LocalizableMessage m = INFO_LDIF_CONNHANDLER_UNKNOWN_CHANGETYPE.get(
               changeRecord.getChangeOperationType().getLDIFChangeType());
          writer.writeComment(m, 78);
        }
        else
        {
          if (logger.isTraceEnabled())
          {
            logger.trace("Result Code: %s", operation.getResultCode());
          }

          LocalizableMessage m = INFO_LDIF_CONNHANDLER_RESULT_CODE.get(
                           operation.getResultCode().intValue(),
                           operation.getResultCode());
          writer.writeComment(m, 78);

          LocalizableMessageBuilder errorMessage = operation.getErrorMessage();
          if (errorMessage != null && errorMessage.length() > 0)
          {
            m = INFO_LDIF_CONNHANDLER_ERROR_MESSAGE.get(errorMessage);
            writer.writeComment(m, 78);
          }

          DN matchedDN = operation.getMatchedDN();
          if (matchedDN != null)
          {
            m = INFO_LDIF_CONNHANDLER_MATCHED_DN.get(matchedDN);
            writer.writeComment(m, 78);
          }

          List<String> referralURLs = operation.getReferralURLs();
          if (referralURLs != null && !referralURLs.isEmpty())
          {
            for (String url : referralURLs)
            {
              m = INFO_LDIF_CONNHANDLER_REFERRAL_URL.get(url);
              writer.writeComment(m, 78);
            }
          }
        }

        writer.writeChangeRecord(changeRecord);
      }
    }
    catch (IOException ioe)
    {
      logger.traceException(ioe);

      fullyProcessed = false;
      LocalizableMessage m = ERR_LDIF_CONNHANDLER_IO_ERROR.get(inputPath,
                                                    getExceptionMessage(ioe));
      logger.error(m);
      DirectoryConfig.sendAlertNotification(this,
                           ALERT_TYPE_LDIF_CONNHANDLER_PARSE_ERROR, m);
    }
    finally
    {
      close(reader, writer);
    }

    if (errorEncountered || !fullyProcessed)
    {
      String renamedPath = inputPath + ".errors-encountered." +
                           TimeThread.getGMTTime();
      if (new File(renamedPath).exists())
      {
        int i=2;
        while (true)
        {
          if (! new File(renamedPath + "." + i).exists())
          {
            renamedPath = renamedPath + "." + i;
          }

          i++;
        }
      }

      try
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Renaming source file to " + renamedPath);
        }

        ldifFile.renameTo(new File(renamedPath));
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage m = ERR_LDIF_CONNHANDLER_CANNOT_RENAME.get(inputPath,
                         renamedPath, getExceptionMessage(e));
        logger.error(m);
        DirectoryConfig.sendAlertNotification(this,
                             ALERT_TYPE_LDIF_CONNHANDLER_IO_ERROR, m);
      }
    }
    else
    {
      try
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Deleting source file");
        }

        ldifFile.delete();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage m = ERR_LDIF_CONNHANDLER_CANNOT_DELETE.get(inputPath,
                         getExceptionMessage(e));
        logger.error(m);
        DirectoryConfig.sendAlertNotification(this,
                             ALERT_TYPE_LDIF_CONNHANDLER_IO_ERROR, m);
      }
    }
  }



  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDIFConnectionHandler(ldifDirectory=\"");
    buffer.append(ldifDirectory.getAbsolutePath());
    buffer.append("\", pollInterval=");
    buffer.append(currentConfig.getPollInterval());
    buffer.append("ms)");
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(ConnectionHandlerCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    LDIFConnectionHandlerCfg cfg = (LDIFConnectionHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
                      LDIFConnectionHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration should always be acceptable.
    return true;
  }



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
                                 LDIFConnectionHandlerCfg configuration)
  {
    // The only processing we need to do here is to get the LDIF directory and
    // create a File object from it.
    File newLDIFDirectory = new File(configuration.getLDIFDirectory());
    this.ldifDirectory = newLDIFDirectory;
    currentConfig = configuration;
    return new ConfigChangeResult();
  }



  /** {@inheritDoc} */
  @Override
  public DN getComponentEntryDN()
  {
    return currentConfig.dn();
  }



  /** {@inheritDoc} */
  public String getClassName()
  {
    return LDIFConnectionHandler.class.getName();
  }



  /** {@inheritDoc} */
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<>();

    alerts.put(ALERT_TYPE_LDIF_CONNHANDLER_PARSE_ERROR,
               ALERT_DESCRIPTION_LDIF_CONNHANDLER_PARSE_ERROR);
    alerts.put(ALERT_TYPE_LDIF_CONNHANDLER_IO_ERROR,
               ALERT_DESCRIPTION_LDIF_CONNHANDLER_IO_ERROR);

    return alerts;
  }
}

