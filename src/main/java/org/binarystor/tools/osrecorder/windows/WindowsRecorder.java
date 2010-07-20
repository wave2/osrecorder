/**
 * Copyright (C) 2008-2010 Wave2 Limited. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of Wave2 Limited nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.binarystor.tools.osrecorder.windows;

import java.io.IOException;
import java.util.logging.Level;
import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.common.JISystem;
import org.jinterop.dcom.core.JIComServer;
import org.jinterop.dcom.core.JIProgId;
import org.jinterop.dcom.core.JISession;
import org.jinterop.dcom.core.JIString;
import org.jinterop.dcom.core.JIVariant;
import org.jinterop.dcom.core.JIArray;
import org.jinterop.dcom.core.IJIComObject;
import org.jinterop.dcom.impls.JIObjectFactory;
import org.jinterop.dcom.impls.automation.IJIEnumVariant;
import org.jinterop.dcom.impls.automation.IJIDispatch;

public class WindowsRecorder {

    private static final String WMI_DEFAULT_NAMESPACE = "ROOT\\CIMV2";
    private String domain;
    private String host;
    private String username;
    private String password;

    private static JISession configAndConnectDCom(String domain, String user, String pass) throws Exception {
        JISystem.getLogger().setLevel(Level.OFF);
        try {
            JISystem.setInBuiltLogHandler(false);
        } catch (IOException ignored) {
        }
        JISystem.setAutoRegisteration(true);
        JISession dcomSession = JISession.createSession(domain, user, pass);
        dcomSession.useSessionSecurity(true);
        return dcomSession;
    }

    private static IJIDispatch getWmiLocator(String host, JISession dcomSession) throws Exception {
        JIComServer wbemLocatorComObj = new JIComServer(JIProgId.valueOf("WbemScripting.SWbemLocator"), host, dcomSession);
        return (IJIDispatch) JIObjectFactory.narrowObject(wbemLocatorComObj.createInstance().queryInterface(IJIDispatch.IID));
    }

    private static IJIDispatch toIDispatch(JIVariant comObjectAsVariant) throws JIException {
        return (IJIDispatch) JIObjectFactory.narrowObject(comObjectAsVariant.getObjectAsComObject());
    }



    public WindowsRecorder(String domain, String host, String user, String password)  {

        JISession dcomSession = null;
try
        {
                dcomSession = configAndConnectDCom( domain, user, password );
                IJIDispatch wbemLocator = getWmiLocator( host, dcomSession );

                JIVariant results[] =
                                wbemLocator.callMethodA( "ConnectServer", new Object[] { new JIString( host ), new JIString( WMI_DEFAULT_NAMESPACE ),
                                                JIVariant.OPTIONAL_PARAM(), JIVariant.OPTIONAL_PARAM(), JIVariant.OPTIONAL_PARAM(), JIVariant.OPTIONAL_PARAM(), new Integer( 0 ),
                                                JIVariant.OPTIONAL_PARAM() } );

                IJIDispatch wbemServices = toIDispatch( results[ 0 ] );

                final String QUERY_FOR_ALL_LOG_EVENTS = "SELECT * FROM Win32_OperatingSystem";
                final int RETURN_IMMEDIATE = 16;
                final int FORWARD_ONLY = 32;

                JIVariant[] eventSourceSet =
                                wbemServices.callMethodA( "ExecQuery", new Object[] { new JIString( QUERY_FOR_ALL_LOG_EVENTS ), new JIString( "WQL" ),
                                                new JIVariant( new Integer( RETURN_IMMEDIATE + FORWARD_ONLY ) ) } );
                IJIDispatch wOSd = (IJIDispatch)JIObjectFactory.narrowObject((eventSourceSet[0]).getObjectAsComObject());

                IJIComObject enumComObject = wOSd.get("_NewEnum").getObjectAsComObject();
                IJIEnumVariant enumVariant = (IJIEnumVariant) JIObjectFactory.narrowObject(enumComObject.queryInterface(IJIEnumVariant.IID));
                IJIDispatch wbemObject_dispatch = null;

                Object[] values = enumVariant.next(1);
                JIArray array = (JIArray)values[0];
                Object[] arrayObj = (Object[])array.getArrayInstance();

                wbemObject_dispatch = (IJIDispatch)JIObjectFactory.narrowObject(((JIVariant)arrayObj[0]).getObjectAsComObject());
                
                System.out.println( wbemObject_dispatch.get("Name").getObjectAsString2() );
                
                //JIVariant variant = (wbemObject_dispatch.callMethodA("GetObjectText_", new Object[]{1}))[0];
                //IJIDispatch eventAsVariant = (IJIDispatch) JIObjectFactory.narrowObject( ( eventSourceSet[ 0 ] ).getObjectAsComObject() );
                //IJIDispatch eventAsVariant = toIDispatch( eventSourceSet[0] );
                        //JIVariant[] eventAsVariant2 =  eventAsVariant.callMethodA( "GetObjectText_", new Object[] { new JIVariant( new Integer(0) ) } );
                        //IJIDispatch wbemEvent = toIDispatch( eventAsVariant );

                        // WMI gives us events as SWbemObject instances (a base class of any WMI object). We know in our case we asked for a specific object
                        // type, so we will go ahead and invoke methods supported by that Win32_NTLogEvent class via the wbemEvent IDispatch pointer.
                        // In this case, we simply call the "GetObjectText_" method that returns us the entire object as a CIM formatted string. We could,
                        // however, ask the object for its property values via wbemEvent.get("PropertyName"). See the j-interop documentation and examples
                        // for how to query COM properties.
                        //JIVariant objTextAsVariant = (JIVariant) ( wbemEvent.callMethodA( "GetObjectText_", new Object[] { new Integer( 1 ) } ) )[ 0 ];
                        //String asText = objTextAsVariant.getObjectAsString().getString();
                        

        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        } finally {
            if (null != dcomSession) {
                try {
                    JISession.destroySession(dcomSession);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}