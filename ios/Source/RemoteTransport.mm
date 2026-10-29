// SPDX-License-Identifier: AGPL-3.0-only
#include "MobileRemote.h"
#import <Foundation/Foundation.h>
#import <Security/Security.h>
#import <CommonCrypto/CommonDigest.h>
#include <condition_variable>
#include <mutex>
struct ZLRemoteRequestState{std::mutex mutex;std::condition_variable changed;juce::MemoryBlock bytes;juce::String pin;bool done=false,failed=false,pinned=false;int status=0,trustError=0,networkError=0;};
@interface ZLRemoteDelegate:NSObject<NSURLSessionDataDelegate,NSURLSessionTaskDelegate>{@public std::shared_ptr<ZLRemoteRequestState> state;}
@end
@implementation ZLRemoteDelegate
-(void)URLSession:(NSURLSession*)session task:(NSURLSessionTask*)task didReceiveChallenge:(NSURLAuthenticationChallenge*)challenge completionHandler:(void (^)(NSURLSessionAuthChallengeDisposition,NSURLCredential*))completion {
    if(![challenge.protectionSpace.authenticationMethod isEqualToString:NSURLAuthenticationMethodServerTrust]){completion(NSURLSessionAuthChallengePerformDefaultHandling,nil);return;}
    SecTrustRef trust=challenge.protectionSpace.serverTrust;SecCertificateRef certificate=trust?SecTrustGetCertificateAtIndex(trust,0):nullptr;bool valid=false;
    if(certificate){CFDataRef der=SecCertificateCopyData(certificate);unsigned char digest[CC_SHA256_DIGEST_LENGTH];CC_SHA256(CFDataGetBytePtr(der),(CC_LONG)CFDataGetLength(der),digest);auto actual=juce::String::toHexString(digest,CC_SHA256_DIGEST_LENGTH,0).toLowerCase();CFRelease(der);if(actual==state->pin){// The desktop QR pins a self-signed certificate without a serverAuth EKU.
        // Validate X.509 validity/signature against only that pinned leaf, as Android does.
        SecPolicyRef policy=SecPolicyCreateBasicX509();SecTrustSetPolicies(trust,policy);CFRelease(policy);NSArray* anchors=@[(id)certificate];SecTrustSetAnchorCertificates(trust,(CFArrayRef)anchors);SecTrustSetAnchorCertificatesOnly(trust,true);CFErrorRef error=nullptr;valid=SecTrustEvaluateWithError(trust,&error);if(error){state->trustError=int(CFErrorGetCode(error));CFRelease(error);}}}
    {std::lock_guard<std::mutex> lock(state->mutex);state->pinned=valid;}
    completion(valid?NSURLSessionAuthChallengeUseCredential:NSURLSessionAuthChallengeCancelAuthenticationChallenge,valid?[NSURLCredential credentialForTrust:trust]:nil);
}
-(void)URLSession:(NSURLSession*)session task:(NSURLSessionTask*)task willPerformHTTPRedirection:(NSHTTPURLResponse*)response newRequest:(NSURLRequest*)request completionHandler:(void (^)(NSURLRequest*))completion{completion(nil);}
-(void)URLSession:(NSURLSession*)session dataTask:(NSURLSessionDataTask*)task didReceiveResponse:(NSURLResponse*)response completionHandler:(void (^)(NSURLSessionResponseDisposition))completion{std::lock_guard<std::mutex> lock(state->mutex);state->status=(int)((NSHTTPURLResponse*)response).statusCode;if(response.expectedContentLength>600000){state->failed=true;completion(NSURLSessionResponseCancel);}else completion(NSURLSessionResponseAllow);}
-(void)URLSession:(NSURLSession*)session dataTask:(NSURLSessionDataTask*)task didReceiveData:(NSData*)data{std::lock_guard<std::mutex> lock(state->mutex);if(state->bytes.getSize()+data.length>600000){state->failed=true;[task cancel];}else state->bytes.append(data.bytes,data.length);}
-(void)URLSession:(NSURLSession*)session task:(NSURLSessionTask*)task didCompleteWithError:(NSError*)error{{std::lock_guard<std::mutex> lock(state->mutex);state->failed=state->failed||error!=nil;state->networkError=int(error.code);state->done=true;}state->changed.notify_one();}
@end
namespace mobile {
var remoteRequest(const String& host,const String& path,const String& pin,const String& token,const var& body){@autoreleasepool{
    if(!StringArray{"/v1/pair","/v1/state","/v1/command","/v1/disconnect"}.contains(path))throw std::runtime_error("Endpoint");
    auto state=std::make_shared<ZLRemoteRequestState>();state->pin=pin;ZLRemoteDelegate* delegate=[[ZLRemoteDelegate alloc] init];delegate->state=state;auto url=String("https://")+host+path;NSMutableURLRequest* request=[NSMutableURLRequest requestWithURL:[NSURL URLWithString:[NSString stringWithUTF8String:url.toRawUTF8()]]];request.timeoutInterval=5;
    if(token.isNotEmpty())[request setValue:[NSString stringWithUTF8String:("Bearer "+token).toRawUTF8()] forHTTPHeaderField:@"Authorization"];
    if(!body.isVoid()){auto json=JSON::toString(body,true);if(json.getNumBytesAsUTF8()>8192){[delegate release];throw std::runtime_error("Command size");}request.HTTPMethod=@"POST";[request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];request.HTTPBody=[NSData dataWithBytes:json.toRawUTF8() length:json.getNumBytesAsUTF8()];}
    NSURLSessionConfiguration* config=[NSURLSessionConfiguration ephemeralSessionConfiguration];config.timeoutIntervalForRequest=5;config.timeoutIntervalForResource=6;config.HTTPCookieStorage=nil;config.URLCredentialStorage=nil;config.URLCache=nil;NSOperationQueue* queue=[[NSOperationQueue alloc] init];queue.maxConcurrentOperationCount=1;NSURLSession* session=[NSURLSession sessionWithConfiguration:config delegate:delegate delegateQueue:queue];NSURLSessionDataTask* task=[session dataTaskWithRequest:request];[task resume];
    std::unique_lock<std::mutex> lock(state->mutex);bool finished=state->changed.wait_for(lock,std::chrono::seconds(7),[&]{return state->done;});bool valid=finished&&!state->failed&&state->pinned&&state->status==200;auto result=valid?JSON::parse(String::fromUTF8(static_cast<const char*>(state->bytes.getData()),int(state->bytes.getSize()))):var();lock.unlock();[session invalidateAndCancel];[delegate release];[queue release];if(!result.isObject())throw std::runtime_error((String("Remote request failed: status=")+String(state->status)+" trust="+String(state->trustError)+" network="+String(state->networkError)).toStdString());return result;
}}
}
