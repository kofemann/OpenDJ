
@echo off
rem CDDL HEADER START
rem
rem The contents of this file are subject to the terms of the
rem Common Development and Distribution License, Version 1.0 only
rem (the "License").  You may not use this file except in compliance
rem with the License.
rem
rem You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
rem or http://forgerock.org/license/CDDLv1.0.html.
rem See the License for the specific language governing permissions
rem and limitations under the License.
rem
rem When distributing Covered Code, include this CDDL HEADER in each
rem file and include the License file at legal-notices/CDDLv1_0.txt.
rem If applicable, add the following below this CDDL HEADER, with the
rem fields enclosed by brackets "[]" replaced with your own identifying
rem information:
rem      Portions Copyright [yyyy] [name of copyright owner]
rem
rem CDDL HEADER END
rem
rem
rem      Copyright 2008-2010 Sun Microsystems, Inc.
rem      Portions Copyright 2011 ForgeRock AS

setlocal

set OPENDJ_INVOKE_CLASS="org.opends.server.tools.dsreplication.ReplicationCliMain"
if "%RECURSIVE_LOCAL_CALL%" == "true" goto callOffline
goto callOnline

:callOffline
set SCRIPT_ARGS=
set SCRIPT_NAME=dsreplication.offline
goto callScript

:callOnline
set SCRIPT_ARGS=-Dorg.opends.server.dsreplicationcallstatus=firstcall
set SCRIPT_NAME=dsreplication
goto callScript

:callScript
for %%i in (%~sf0) do call "%%~dPsi\..\lib\_server-script.bat" %*
