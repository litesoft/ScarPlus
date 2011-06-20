package com.esotericsoftware.scar;

import java.io.*;
import java.net.*;

import org.apache.commons.net.ftp.*;

import com.esotericsoftware.wildcard.*;

import static com.esotericsoftware.minlog.Log.*;

public class FTP
{
    static public boolean upload( String server, String user, String password, String dir, Paths paths, boolean passive )
            throws IOException
    {
        FTPClient ftp = new FTPClient();
        InetAddress address = InetAddress.getByName( server );
        if ( DEBUG )
        {
            debug( "Connecting to FTP server: " + address );
        }
        ftp.connect( address );
        if ( passive )
        {
            ftp.enterLocalPassiveMode();
        }
        if ( !ftp.login( user, password ) )
        {
            if ( ERROR )
            {
                error( "FTP login failed for user: " + user );
            }
            return false;
        }
        if ( !ftp.changeWorkingDirectory( dir ) )
        {
            if ( ERROR )
            {
                error( "FTP directory change failed: " + dir );
            }
            return false;
        }
        ftp.setFileType( org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE );
        for ( String path : paths )
        {
            if ( INFO )
            {
                info( "FTP upload: " + path );
            }
            BufferedInputStream input = new BufferedInputStream( new FileInputStream( path ) );
            try
            {
                ftp.storeFile( new File( path ).getName(), input );
            }
            finally
            {
                try
                {
                    input.close();
                }
                catch ( Exception ignored )
                {
                }
            }
        }
        ftp.logout();
        ftp.disconnect();
        return true;
    }
}
