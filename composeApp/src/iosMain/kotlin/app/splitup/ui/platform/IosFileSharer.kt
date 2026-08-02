package app.splitup.ui.platform

import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

class IosFileSharer : FileSharer {
    override fun shareTextFile(fileName: String, mimeType: String, content: String) {
        val path = NSTemporaryDirectory() + fileName
        @Suppress("CAST_NEVER_SUCCEEDS")
        (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)

        val controller = UIActivityViewController(
            activityItems = listOf(NSURL.fileURLWithPath(path)),
            applicationActivities = null,
        )
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(controller, animated = true, completion = null)
    }
}
