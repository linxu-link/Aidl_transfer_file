package com.example.aidl;

interface IOptions {
    void transactFileDescriptor(in ParcelFileDescriptor pfd);
}