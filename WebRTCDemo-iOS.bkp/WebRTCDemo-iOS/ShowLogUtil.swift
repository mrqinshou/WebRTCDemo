//
//  ShowLogUtil.swift
//  WebRTCDemo-iOS
//
//  Created by 覃浩 on 2023/2/11.
//

import Foundation

open class ShowLogUtil {
    public enum LogLevel: Int {
        case VERBOSE = 0
        case DEBUG = 1
        case INFO = 2
        case WRANING = 3
        case ERROR = 4
        case NONE = 5
    }
    
    private static let dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
    private static let dateFormatter = DateFormatter()
    private static let bundleIdentifier = Bundle.main.bundleIdentifier ?? ProcessInfo.processInfo.processName
//    private static let processName = ProcessInfo.processInfo.processName
    private static let processIdentifier = ProcessInfo.processInfo.processIdentifier
    private static var logLevel = LogLevel.VERBOSE
    
    private static func getTid() -> UInt64{
        var tid = UInt64(0)
        pthread_threadid_np(nil, &tid)
        return tid
    }
    
    public static func setLogLevel(logLevel: LogLevel) {
        self.logLevel = logLevel
    }
    
    public static func verbose(tag: String = "daolema", _ log: Any, file: NSString = #file, line: Int = #line, function: NSString = #function) {
        if (logLevel.rawValue > LogLevel.VERBOSE.rawValue) {
            return
        }
        dateFormatter.dateFormat = dateFormat
        let date = dateFormatter.string(from: Date())
        print("💜 \(date) \(processIdentifier)-\(getTid())/\(bundleIdentifier) V/\(tag) (\(file.lastPathComponent):\(line)): \(log)")
    }
    
    public static func debug(tag: String = "daolema", _ log: Any, file: NSString = #file, line: Int = #line, function: NSString = #function) {
        if (logLevel.rawValue > LogLevel.DEBUG.rawValue) {
            return
        }
        dateFormatter.dateFormat = dateFormat
        let date = dateFormatter.string(from: Date())
        print("💚 \(date) \(processIdentifier)-\(getTid())/\(bundleIdentifier) D/\(tag) (\(file.lastPathComponent):\(line)): \(log)")
    }
    
    public static func info(tag: String = "daolema", _ log: Any, file: NSString = #file, line: Int = #line, function: NSString = #function) {
        if (logLevel.rawValue > LogLevel.INFO.rawValue) {
            return
        }
        dateFormatter.dateFormat = dateFormat
        let date = dateFormatter.string(from: Date())
        print("💙 \(date) \(processIdentifier)-\(getTid())/\(bundleIdentifier) I/\(tag) (\(file.lastPathComponent):\(line)): \(log)")
    }
    
    public static func warning(tag: String = "daolema", _ log: Any, file: NSString = #file, line: Int = #line, function: NSString = #function) {
        if (logLevel.rawValue > LogLevel.WRANING.rawValue) {
            return
        }
        dateFormatter.dateFormat = dateFormat
        let date = dateFormatter.string(from: Date())
        print("💛 \(date) \(processIdentifier)-\(getTid())/\(bundleIdentifier) W/\(tag) (\(file.lastPathComponent):\(line)): \(log)")
    }
    
    public static func error(tag: String = "daolema", _ log: Any, file: NSString = #file, line: Int = #line, function: NSString = #function) {
        if (logLevel.rawValue > LogLevel.ERROR.rawValue) {
            return
        }
        dateFormatter.dateFormat = dateFormat
        let date = dateFormatter.string(from: Date())
        print("❤️ \(date) \(processIdentifier)-\(getTid())/\(bundleIdentifier) E/\(tag) (\(file.lastPathComponent):\(line)): \(log)")
    }
}

