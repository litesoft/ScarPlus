package com.esotericsoftware.wildcard;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import java.util.zip.*;

/**
 * Collects filesystem paths using wildcards, preserving the directory structure. Copies, deletes, and zips paths.
 */
public class Paths implements Iterable<String>
{
    static private final Comparator<Path> LONGEST_TO_SHORTEST = new Comparator<Path>()
    {
        @Override
        public int compare( Path s1, Path s2 )
        {
            return s2.absolute().length() - s1.absolute().length();
        }
    };

    static private List<String> defaultGlobExcludes;

    private final HashSet<Path> paths = new HashSet<Path>( 32 );

    /**
     * Creates an empty Paths object.
     */
    public Paths()
    {
    }

    /**
     * Creates a Paths object and calls {@link #glob(String, String[])} with the specified arguments.
     */
    public Paths( String dir, String... patterns )
    {
        glob( dir, patterns );
    }

    /**
     * Creates a Paths object and calls {@link #glob(String, List)} with the specified arguments.
     */
    public Paths( String dir, List<String> patterns )
    {
        glob( dir, patterns );
    }

    /**
     * Collects all files and directories in the specified directory matching the wildcard patterns.
     *
     * @param dir      The directory containing the paths to collect. If it does not exist, no paths are collected. If null, "." is
     *                 assumed.
     * @param patterns The wildcard patterns of the paths to collect or exclude. Patterns may optionally contain wildcards
     *                 represented by asterisks and question marks. If empty or omitted then the dir parameter is split on the "|"
     *                 character, the first element is used as the directory and remaining are used as the patterns. If null, ** is
     *                 assumed (collects all paths).<br>
     *                 <br>
     *                 A single question mark (?) matches any single character. Eg, something? collects any path that is named
     *                 "something" plus any character.<br>
     *                 <br>
     *                 A single asterisk (*) matches any characters up to the next slash (/). Eg, *\*\something* collects any path that
     *                 has two directories of any name, then a file or directory that starts with the name "something".<br>
     *                 <br>
     *                 A double asterisk (**) matches any characters. Eg, **\something\** collects any path that contains a directory
     *                 named "something".<br>
     *                 <br>
     *                 A pattern starting with an exclamation point (!) causes paths matched by the pattern to be excluded, even if other
     *                 patterns would select the paths.
     */
    public void glob( String dir, String... patterns )
    {
        if ( dir == null )
        {
            dir = ".";
        }
        if ( patterns != null && patterns.length == 0 )
        {
            String[] split = dir.split( "\\|" ); // split on a '|'
            if ( split.length > 1 )
            {
                dir = split[0];
                patterns = new String[split.length - 1];
                for ( int i = 1, n = split.length; i < n; i++ )
                {
                    patterns[i - 1] = split[i];
                }
            }
        }
        File dirFile = new File( dir );
        if ( !dirFile.exists() )
        {
            return;
        }

        List<String> includes = new ArrayList<String>();
        List<String> excludes = new ArrayList<String>();
        if ( patterns != null )
        {
            for ( String pattern : patterns )
            {
                if ( pattern.charAt( 0 ) == '!' )
                {
                    excludes.add( pattern.substring( 1 ) );
                }
                else
                {
                    includes.add( pattern );
                }
            }
        }
        if ( includes.isEmpty() )
        {
            includes.add( "**" );
        }

        if ( defaultGlobExcludes != null )
        {
            excludes.addAll( defaultGlobExcludes );
        }

        GlobScanner scanner = new GlobScanner( dirFile, includes, excludes );
        String rootDir = scanner.rootDir().getPath().replace( '\\', '/' );
        if ( !rootDir.endsWith( "/" ) )
        {
            rootDir += '/';
        }
        for ( String filePath : scanner.matches() )
        {
            paths.add( new Path( rootDir, filePath ) );
        }
    }

    /**
     * Calls {@link #glob(String, String...)}.
     */
    public void glob( String dir, List<String> patterns )
    {
        if ( patterns == null )
        {
            throw new IllegalArgumentException( "patterns cannot be null." );
        }
        glob( dir, patterns.toArray( new String[patterns.size()] ) );
    }

    /**
     * Collects all files and directories in the specified directory matching the regular expression patterns. This method is much
     * slower than {@link #glob(String, String...)} because every file and directory under the specified directory must be
     * inspected.
     *
     * @param dir      The directory containing the paths to collect. If it does not exist, no paths are collected.
     * @param patterns The regular expression patterns of the paths to collect or exclude. If empty or omitted then the dir
     *                 parameter is split on the "|" character, the first element is used as the directory and remaining are used as the
     *                 patterns. If null, ** is assumed (collects all paths).<br>
     *                 <br>
     *                 A pattern starting with an exclamation point (!) causes paths matched by the pattern to be excluded, even if other
     *                 patterns would select the paths.
     */
    public void regex( String dir, String... patterns )
    {
        if ( dir == null )
        {
            dir = ".";
        }
        if ( patterns != null && patterns.length == 0 )
        {
            String[] split = dir.split( "\\|" );
            if ( split.length > 1 )
            {
                dir = split[0];
                patterns = new String[split.length - 1];
                for ( int i = 1, n = split.length; i < n; i++ )
                {
                    patterns[i - 1] = split[i];
                }
            }
        }
        File dirFile = new File( dir );
        if ( !dirFile.exists() )
        {
            return;
        }

        List<String> includes = new ArrayList();
        List<String> excludes = new ArrayList();
        if ( patterns != null )
        {
            for ( String pattern : patterns )
            {
                if ( pattern.charAt( 0 ) == '!' )
                {
                    excludes.add( pattern.substring( 1 ) );
                }
                else
                {
                    includes.add( pattern );
                }
            }
        }
        if ( includes.isEmpty() )
        {
            includes.add( ".*" );
        }

        RegexScanner scanner = new RegexScanner( dirFile, includes, excludes );
        String rootDir = scanner.rootDir().getPath().replace( '\\', '/' );
        if ( !rootDir.endsWith( "/" ) )
        {
            rootDir += '/';
        }
        for ( String filePath : scanner.matches() )
        {
            paths.add( new Path( rootDir, filePath ) );
        }
    }

    /**
     * Copies the files and directories to the specified directory.
     *
     * @return A paths object containing the paths of the new files.
     */
    public Paths copyTo( String destDir )
            throws IOException
    {
        Paths newPaths = new Paths();
        for ( Path path : paths )
        {
            File destFile = new File( destDir, path.name );
            File srcFile = path.file();
            if ( srcFile.isDirectory() )
            {
                destFile.mkdirs();
            }
            else
            {
                destFile.getParentFile().mkdirs();
                copyFile( srcFile, destFile );
            }
            newPaths.paths.add( new Path( destDir, path.name ) );
        }
        return newPaths;
    }

    /**
     * Deletes all the files, directories, and any files in the directories.
     *
     * @return False if any file could not be deleted.
     */
    public boolean delete()
    {
        boolean success = true;
        List<Path> pathsCopy = new ArrayList<Path>( paths );
        Collections.sort( pathsCopy, LONGEST_TO_SHORTEST );
        for ( File file : getFiles( pathsCopy ) )
        {
            if ( file.isDirectory() )
            {
                if ( !deleteDirectory( file ) )
                {
                    success = false;
                }
            }
            else
            {
                if ( !file.delete() )
                {
                    success = false;
                }
            }
        }
        return success;
    }

    /**
     * Compresses the files and directories specified by the paths into a new zip file at the specified location. If there are no
     * paths or all the paths are directories, no zip file will be created.
     */
    public void zip( String destFile )
            throws IOException
    {
        Paths zipPaths = filesOnly();
        if ( zipPaths.paths.isEmpty() )
        {
            return;
        }
        byte[] buf = new byte[1024];
        ZipOutputStream out = new ZipOutputStream( new FileOutputStream( destFile ) );
        try
        {
            for ( Path path : zipPaths.paths )
            {
                File file = path.file();
                out.putNextEntry( new ZipEntry( path.name.replace( '\\', '/' ) ) );
                FileInputStream in = new FileInputStream( file );
                int len;
                while ( (len = in.read( buf )) > 0 )
                {
                    out.write( buf, 0, len );
                }
                in.close();
                out.closeEntry();
            }
        }
        finally
        {
            out.close();
        }
    }

    public int count()
    {
        return paths.size();
    }

    public boolean isEmpty()
    {
        return paths.isEmpty();
    }

    /**
     * Returns the absolute paths delimited by the specified character.
     */
    public String toString( String delimiter )
    {
        StringBuffer buffer = new StringBuffer( 256 );
        for ( String path : getPaths() )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( delimiter );
            }
            buffer.append( path );
        }
        return buffer.toString();
    }

    /**
     * Returns the absolute paths delimited by commas.
     */
    public String toString()
    {
        return toString( ", " );
    }

    /**
     * Returns a Paths object containing the paths that are files, as if each file were selected from its parent directory.
     */
    public Paths flatten()
    {
        Paths newPaths = new Paths();
        for ( Path path : paths )
        {
            File file = path.file();
            if ( file.isFile() )
            {
                newPaths.paths.add( new Path( file.getParent(), file.getName() ) );
            }
        }
        return newPaths;
    }

    /**
     * Returns a Paths object containing the paths that are files.
     */
    public Paths filesOnly()
    {
        Paths newPaths = new Paths();
        for ( Path path : paths )
        {
            if ( path.file().isFile() )
            {
                newPaths.paths.add( path );
            }
        }
        return newPaths;
    }

    /**
     * Returns a Paths object containing the paths that are directories.
     */
    public Paths dirsOnly()
    {
        Paths newPaths = new Paths();
        for ( Path path : paths )
        {
            if ( path.file().isDirectory() )
            {
                newPaths.paths.add( path );
            }
        }
        return newPaths;
    }

    /**
     * Returns the paths as File objects.
     */
    public List<File> getFiles()
    {
        return getFiles( new ArrayList( paths ) );
    }

    private ArrayList<File> getFiles( List<Path> paths )
    {
        ArrayList<File> files = new ArrayList( paths.size() );
        int i = 0;
        for ( Path path : paths )
        {
            files.add( path.file() );
        }
        return files;
    }

    /**
     * Returns the portion of the path after the root directory where the path was collected.
     */
    public List<String> getRelativePaths()
    {
        ArrayList<String> stringPaths = new ArrayList( paths.size() );
        int i = 0;
        for ( Path path : paths )
        {
            stringPaths.add( path.name );
        }
        return stringPaths;
    }

    /**
     * Returns the full paths.
     */
    public List<String> getPaths()
    {
        ArrayList<String> stringPaths = new ArrayList( paths.size() );
        int i = 0;
        for ( File file : getFiles() )
        {
            stringPaths.add( file.getPath() );
        }
        return stringPaths;
    }

    /**
     * Returns the paths' filenames.
     */
    public List<String> getNames()
    {
        ArrayList<String> stringPaths = new ArrayList( paths.size() );
        int i = 0;
        for ( File file : getFiles() )
        {
            stringPaths.add( file.getName() );
        }
        return stringPaths;
    }

    /**
     * Adds a single path to this Paths object.
     */
    public void add( String dir, String name )
    {
        paths.add( new Path( dir, name ) );
    }

    /**
     * Adds all paths from the specified Paths object to this Paths object.
     */
    public void add( Paths paths )
    {
        this.paths.addAll( paths.paths );
    }

    /**
     * Iterates over the absolute paths. The iterator supports the remove method.
     */
    @Override
    public Iterator<String> iterator()
    {
        return new Iterator<String>()
        {
            private Iterator<Path> iter = paths.iterator();

            @Override
            public void remove()
            {
                iter.remove();
            }

            @Override
            public String next()
            {
                return iter.next().absolute();
            }

            @Override
            public boolean hasNext()
            {
                return iter.hasNext();
            }
        };
    }

    /**
     * Iterates over the paths as File objects. The iterator supports the remove method.
     */
    public Iterator<File> fileIterator()
    {
        return new Iterator<File>()
        {
            private Iterator<Path> iter = paths.iterator();

            @Override
            public void remove()
            {
                iter.remove();
            }

            @Override
            public File next()
            {
                return iter.next().file();
            }

            @Override
            public boolean hasNext()
            {
                return iter.hasNext();
            }
        };
    }

    static private final class Path
    {
        public final String dir;
        public final String name;

        public Path( String dir, String name )
        {
            this.dir = dir;
            this.name = name;
        }

        public String absolute()
        {
            return dir + name;
        }

        public File file()
        {
            return new File( dir, name );
        }

        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((dir == null) ? 0 : dir.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        public boolean equals( Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            if ( obj == null )
            {
                return false;
            }
            if ( getClass() != obj.getClass() )
            {
                return false;
            }
            Path other = (Path) obj;
            if ( dir == null )
            {
                if ( other.dir != null )
                {
                    return false;
                }
            }
            else if ( !dir.equals( other.dir ) )
            {
                return false;
            }
            if ( name == null )
            {
                if ( other.name != null )
                {
                    return false;
                }
            }
            else if ( !name.equals( other.name ) )
            {
                return false;
            }
            return true;
        }
    }

    /**
     * Sets the exclude patterns that will be used in addition to the excludes specified for all glob searches.
     */
    static public void setDefaultGlobExcludes( String... defaultGlobExcludes )
    {
        Paths.defaultGlobExcludes = Arrays.asList( defaultGlobExcludes );
    }

    /**
     * Copies one file to another.
     */
    static private void copyFile( File in, File out )
            throws IOException
    {
        FileChannel sourceChannel = new FileInputStream( in ).getChannel();
        FileChannel destinationChannel = new FileOutputStream( out ).getChannel();
        sourceChannel.transferTo( 0, sourceChannel.size(), destinationChannel );
        sourceChannel.close();
        destinationChannel.close();
    }

    /**
     * Deletes a directory and all files and directories it contains.
     */
    static private boolean deleteDirectory( File file )
    {
        if ( file.exists() )
        {
            File[] files = file.listFiles();
            for ( int i = 0, n = files.length; i < n; i++ )
            {
                if ( files[i].isDirectory() )
                {
                    deleteDirectory( files[i] );
                }
                else
                {
                    files[i].delete();
                }
            }
        }
        return file.delete();
    }

    public static void main( String[] args )
    {
        if ( args.length == 0 )
        {
            System.out.println( "Usage: dir [pattern] [, pattern ...]" );
            System.exit( 0 );
        }
        List<String> patterns = Arrays.asList( args );
        patterns = patterns.subList( 1, patterns.size() );
        for ( String path : new Paths( args[0], patterns ) )
        {
            System.out.println( path );
        }
    }
}
