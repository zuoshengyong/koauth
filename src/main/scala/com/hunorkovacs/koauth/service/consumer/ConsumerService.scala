package com.hunorkovacs.koauth.service.consumer

import java.util.{Calendar, TimeZone}

import com.hunorkovacs.koauth.domain.OauthParams._
import com.hunorkovacs.koauth.domain.Request
import com.hunorkovacs.koauth.service.Arithmetics.{sign, concatItemsForSignature, createAuthorizationHeader}
import com.hunorkovacs.koauth.service.Generator.generateNonce

import scala.concurrent.{ExecutionContext, Future}

trait ConsumerService {

  def createRequestTokenRequest(request: Request,
                                consumerKey: String,
                                consumerSecret: String,
                                callback: String)
                               (implicit ec: ExecutionContext): Future[String]

  def createAuthorizeRequest(request: Request,
                             consumerKey: String,
                             requestToken: String,
                             username: String,
                             password: String)
                            (implicit ec: ExecutionContext): Future[String]

  def createAccessTokenRequest(request: Request,
                               consumerKey: String,
                               consumerSecret: String,
                               requestToken: String,
                               requestTokenSecret: String,
                               verifier: String)
                              (implicit ec: ExecutionContext): Future[String]

  def createOauthenticatedRequest(request: Request,
                                  consumerKey: String,
                                  consumerSecret: String,
                                  requestToken: String,
                                  requestTokenSecret: String)
                                 (implicit ec: ExecutionContext): Future[String]

  def createGeneralSignedRequest(request: Request)
                                (implicit ec: ExecutionContext): Future[String]
}

object DefaultConsumerService extends ConsumerService {

  private val CalendarGMT = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

  override def createRequestTokenRequest(request: Request,
                                         consumerKey: String,
                                         consumerSecret: String,
                                         callback: String)
                                        (implicit ec: ExecutionContext): Future[String] = {
    Future {
      val paramsList = createBasicParamList().::((consumerKeyName, consumerKey))
        .::((consumerSecretName, consumerSecret))
        .::((callbackName, callback))
      Request(request, paramsList)
    }.flatMap(createGeneralSignedRequest)
  }

  override def createAuthorizeRequest(request: Request,
                                      consumerKey: String,
                                      requestToken: String,
                                      username: String,
                                      password: String)
                                     (implicit ec: ExecutionContext): Future[String] = {
    Future {
      val paramsList = createBasicParamList().::((consumerKeyName, consumerKey))
        .::((tokenName, requestToken))
        .::((usernameName, username))
        .::((passwordName, password))
      createAuthorizationHeader(paramsList)
    }
  }

  override def createAccessTokenRequest(request: Request,
                                        consumerKey: String,
                                        consumerSecret: String,
                                        requestToken: String,
                                        requestTokenSecret: String,
                                        verifier: String)
                                       (implicit ec: ExecutionContext): Future[String] = {
    Future {
      val paramsList = createBasicParamList().::((consumerKeyName, consumerKey))
      .::((consumerSecretName, consumerSecret))
      .::((tokenName, requestToken))
      .::((tokenSecretName, requestTokenSecret))
      .::((verifierName, verifier))
      Request(request, paramsList)
    }.flatMap(createGeneralSignedRequest)
  }

  override def createOauthenticatedRequest(request: Request,
                                           consumerKey: String,
                                           consumerSecret: String,
                                           requestToken: String,
                                           requestTokenSecret: String)
                                          (implicit ec: ExecutionContext): Future[String] = {
    Future {
      val paramsList = createBasicParamList().::((consumerKeyName, consumerKey))
        .::((consumerSecretName, consumerSecret))
        .::((tokenName, requestToken))
        .::((tokenSecretName, requestTokenSecret))
      Request(request, paramsList)
    }.flatMap(createGeneralSignedRequest)
  }

  private def createBasicParamList(): List[(String, String)] = {
    List((nonceName, generateNonce),
      (versionName, "1.0"),
      (signatureMethodName, "HMAC-SHA1"),
      (timestampName, CalendarGMT.getTimeInMillis.toString))
  }

  def createGeneralSignedRequest(request: Request)
                                (implicit ec: ExecutionContext): Future[String] = {
    Future {
      val signature = signRequest(request)
      val list = request.oauthParamsList
        .filterNot(param => consumerSecretName == param._1 || tokenSecretName == param._1)
        .::((signatureName, signature))
      createAuthorizationHeader(list)
    }
  }

  def createSignatureBase(request: Request): String = {
    val filteredList = request.oauthParamsList
      .filterNot(param => consumerSecretName == param._1 || tokenSecretName == param._1)
    concatItemsForSignature(new Request(request.method,
      request.urlWithoutParams,
      request.urlParams,
      request.bodyParams,
      filteredList,
      filteredList.toMap))
  }

  def signRequest(request: Request): String = {
    val base = createSignatureBase(request)
    val consumerSecret = request.oauthParamsMap.applyOrElse(consumerSecretName, (s: String) => "")
    val tokenSecret = request.oauthParamsMap.applyOrElse(tokenSecretName, (s: String) => "")
    sign(base, consumerSecret, tokenSecret)
  }
}