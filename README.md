
最近在写车载Android的第5篇视频教程「AIDL的实践与封装」时，遇到一个有意思的问题，能不能通过AIDL传输超过 1M 以上的文件？

我们先不细究，为什么要用AIDL传递大文件，单纯从技术的角度考虑能不能实现。众所周知，AIDL是一种基于Binder实现的跨进程调用方案，Binder 对传输数据大小有限制，传输超过 1M 的文件就会报 android.os.TransactionTooLargeException 异常。

如果文件相对比较小，还可以将文件分片，大不了多调用几次AIDL接口，但是当遇到大型文件或超大型文件时，这种方法就显得耗时又费力。好在，Android 系统提供了现成的解决方案，其中一种解决办法是，使用AIDL传递文件描述符ParcelFileDescriptor，来实现超大型文件的跨进程传输。



## ParcelFileDescriptor

ParcelFileDescriptor 是一个实现了 Parcelable 接口的类，它封装了一个文件描述符 (FileDescriptor)，可以通过 Binder 将它传递给其他进程，从而实现跨进程访问文件或网络套接字。ParcelFileDescriptor 也可以用来创建管道 (pipe)，用于进程间的数据流传输。

ParcelFileDescriptor 的具体用法有以下几种：

-   通过 ParcelFileDescriptor.createPipe() 方法创建一对 ParcelFileDescriptor 对象，分别用于读写管道中的数据，实现进程间的数据流传输。
-   通过 ParcelFileDescriptor.fromSocket() 方法将一个网络套接字 (Socket)转换为一个 ParcelFileDescriptor 对象，然后通过 Binder 将它传递给其他进程，实现跨进程访问网络套接字。
-   通过 ParcelFileDescriptor.open() 方法打开一个文件，并返回一个 ParcelFileDescriptor 对象，然后通过 Binder 将它传递给其他进程，实现跨进程访问文件。
-   通过 ParcelFileDescriptor.close() 方法关闭一个 ParcelFileDescriptor 对象，释放其占用的资源。



ParcelFileDescriptor.createPipe()和ParcelFileDescriptor.open() 都可以实现，跨进程文件传输，接下来我们会分别演示。

## 实践

-   **第一步，定义AIDL接口**

```
interface IOptions {
    void transactFileDescriptor(in ParcelFileDescriptor pfd);
}
```




-  **第二步，在「传输方」使用`ParcelFileDescriptor.open`实现文件发送**

```
private void transferData() {
    try {
        // file.iso 是要传输的文件，位于app的缓存目录下，约3.5GB
        ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(new File(getCacheDir(), "file.iso"), ParcelFileDescriptor.MODE_READ_ONLY);
        // 调用AIDL接口，将文件描述符的读端 传递给 接收方
        options.transactFileDescriptor(fileDescriptor);
        fileDescriptor.close();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
```




-   **或，在「传输方」使用`ParcelFileDescriptor.createPipe`实现文件发送**

ParcelFileDescriptor.createPipe 方法会返回一个数组，数组中的第一个元素是管道的读端，第二个元素是管道的写端。

使用时，我们先将「读端-文件描述符」使用AIDL发给「接收端」，然后将文件流写入「写端」的管道即可。

```
    private void transferData() {
        try {
/******** 下面的方法也可以实现文件传输，「接收端」不需要任何修改，原理是一样的 ********/
//        createReliablePipe 创建一个管道，返回一个 ParcelFileDescriptor 数组，
//        数组中的第一个元素是管道的读端，
//        第二个元素是管道的写端
            ParcelFileDescriptor[] pfds = ParcelFileDescriptor.createReliablePipe();
            ParcelFileDescriptor pfdRead = pfds[0];
            // 调用AIDL接口，将管道的读端传递给 接收端
            options.transactFileDescriptor(pfdRead);
            ParcelFileDescriptor pfdWrite = pfds[1];
            // 将文件写入到管道中
            byte[] buffer = new byte[1024];
            int len;
            try (
                    // file.iso 是要传输的文件，位于app的缓存目录下
                    FileInputStream inputStream = new FileInputStream(new File(getCacheDir(), "file.iso"));
                    ParcelFileDescriptor.AutoCloseOutputStream autoCloseOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pfdWrite);
            ) {
                while ((len = inputStream.read(buffer)) != -1) {
                    autoCloseOutputStream.write(buffer, 0, len);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

> 注意，管道写入的文件流 总量限制在64KB，所以「接收方」要及时将文件从管道中读出，否则「传输方」的写入操作会一直阻塞。




-   **第三步，在「接收方」读取文件流并保存到本地**

```
private final IOptions.Stub options = new IOptions.Stub() {
    @Override
    public void transactFileDescriptor(ParcelFileDescriptor pfd) {
        Log.i(TAG, "transactFileDescriptor: " + Thread.currentThread().getName());
        Log.i(TAG, "transactFileDescriptor: calling pid:" + Binder.getCallingPid() + " calling uid:" + Binder.getCallingUid());
        File file = new File(getCacheDir(), "file.iso");
        try (
                ParcelFileDescriptor.AutoCloseInputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        ) {
            file.delete();
            file.createNewFile();
            FileOutputStream stream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int len;
            // 将inputStream中的数据写入到file中
            while ((len = inputStream.read(buffer)) != -1) {
                stream.write(buffer, 0, len);
            }
            stream.close();
            pfd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
};
```




-   **运行程序**

在程序运行之前，需要将一个大型文件放置到client app的缓存目录下，用于测试。目录地址：data/data/com.example.server/cache。

> 注意：如果使用模拟器测试，模拟器的硬盘要预留 3.5GB * 2 的闲置空间。

将程序运行起来，可以发现，3.5GB 的 file.iso 顺利传输到了Server端。

![](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/35101092225e4fcaa8a8521eb0fd807c~tplv-k3u1fbpfcp-zoom-1.image)




大文件是可以传输了，那么使用这种方式会很耗费内存吗？我们继续在文件传输时，查看一下内存占用的情况，如下所示：

-   **传输方-Client，内存使用情况**

![](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/a6a0f4d0177a4d16a79ff01b114c9ce4~tplv-k3u1fbpfcp-zoom-1.image)

-   **接收方-Server，内存使用情况**

![](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/0f6fe01651924e96b435dc45daf9c1b5~tplv-k3u1fbpfcp-zoom-1.image)

从Android Studio Profiler给出的内存取样数据可以看出，无论是传输方还是接收方的内存占用都非常的克制、平缓。




## 总结

在编写本文之前，我在掘金上还看到了另一篇文章：[一道面试题:使用AIDL实现跨进程传输一个2M大小的文件 - 掘金](https://juejin.cn/post/6990379493235884062)

该文章与本文类似，都是使用AIDL向接收端传输`ParcelFileDescriptor`，不过该文中使用共享内存MemoryFile构造出`ParcelFileDescriptor`，MemoryFile的创建需要使用反射，对于使用MemoryFile映射超大型文件是否会导致内存占用过大的问题，我个人没有尝试，欢迎有兴趣的朋友进行实践。




总得来 ParcelFileDescriptor 和 MemoryFile 的区别有以下几点：

-   ParcelFileDescriptor 是一个封装了文件描述符的类，可以通过 Binder 传递给其他进程，实现跨进程访问文件或网络套接字。MemoryFile 是一个封装了匿名共享内存的类，可以通过反射获取其文件描述符，然后通过 Binder 传递给其他进程，实现跨进程访问共享内存。
-   ParcelFileDescriptor 可以用来打开任意的文件或网络套接字，而 MemoryFile 只能用来创建固定大小的共享内存。
-   ParcelFileDescriptor 可以通过 ParcelFileDescriptor.createPipe() 方法创建一对 ParcelFileDescriptor 对象，分别用于读写管道中的数据，实现进程间的数据流传输。MemoryFile 没有这样的方法，但可以通过 MemoryFile.getInputStream() 和 MemoryFile.getOutputStream() 方法获取输入输出流，实现进程内的数据流传输。



在其他领域的应用方面，ParcelFileDescriptor 和 MemoryFile也有着性能上的差异，主要取决于两个方面：

-   数据的大小和类型。

如果数据是大型的文件或网络套接字，那么使用 ParcelFileDescriptor 可能更合适，因为它可以直接传递文件描述符，而不需要复制数据。如果数据是小型的内存块，那么使用 MemoryFile 可能更合适，因为它可以直接映射到物理内存，而不需要打开文件或网络套接字。

-   数据的访问方式。

如果数据是需要频繁读写的，那么使用 MemoryFile 可能更合适，因为它可以提供输入输出流，实现进程内的数据流传输。如果数据是只需要一次性读取的，那么使用 ParcelFileDescriptor 可能更合适，因为它可以通过 ParcelFileDescriptor.createPipe() 方法创建一对 ParcelFileDescriptor 对象，分别用于读写管道中的数据，实现进程间的数据流传输。




好了，以上就是本文的所有内容了，感谢你的阅读，希望对你有所帮助。
