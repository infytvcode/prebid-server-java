package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.bidder.Rubicon
import org.prebid.server.functional.model.deals.lineitem.LineItemSize
import org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator
import org.prebid.server.functional.model.deals.lineitem.targeting.Targeting
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Bidder
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.GeoExt
import org.prebid.server.functional.model.request.auction.GeoExtNetAcuity
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExt
import org.prebid.server.functional.model.request.auction.ImpExtContext
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.ImpExtContextDataAdServer
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserTime
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static java.time.temporal.WeekFields.SUNDAY_START
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator.NOT
import static org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator.OR
import static org.prebid.server.functional.model.deals.lineitem.targeting.BooleanOperator.UPPERCASE_AND
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.IN
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.INTERSECTS
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.MATCHES
import static org.prebid.server.functional.model.deals.lineitem.targeting.MatchingFunction.WITHIN
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.AD_UNIT_AD_SLOT
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.AD_UNIT_MEDIA_TYPE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.AD_UNIT_SIZE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.APP_BUNDLE
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.BIDP_ACCOUNT_ID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.DEVICE_METRO
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.DEVICE_REGION
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.DOW
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.HOUR
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.INVALID
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.PAGE_POSITION
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.REFERRER
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.SITE_DOMAIN
import static org.prebid.server.functional.model.deals.lineitem.targeting.TargetingType.UFPD_BUYER_UID
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

class TargetingSpec extends BasePgSpec {

    @Shared
    String stringTargetingValue = PBSUtils.randomString
    @Shared
    Integer integerTargetingValue = PBSUtils.randomNumber

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should invalidate line items when targeting has #reason"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = targeting
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't had PG deals auction as line item hasn't passed validation"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        reason                                         | targeting

        "two root nodes"                               | Targeting.invalidTwoRootNodesTargeting

        "invalid boolean operator"                     | new Targeting.Builder(BooleanOperator.INVALID).addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                                       .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [BANNER])
                                                                                                       .build()

        "uppercase boolean operator"                   | new Targeting.Builder(UPPERCASE_AND).addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                             .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [BANNER])
                                                                                             .build()

        "invalid targeting type"                       | Targeting.defaultTargetingBuilder
                                                                  .addTargeting(INVALID, INTERSECTS, [PBSUtils.randomString])
                                                                  .build()

        "'in' matching type value as not list"         | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, IN, BANNER)
                                                                                .build()

        "'intersects' matching type value as not list" | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, BANNER)
                                                                                .build()

        "'within' matching type value as not list"     | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, WITHIN, BANNER)
                                                                                .build()

        "'matches' matching type value as list"        | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, MATCHES, [BANNER])
                                                                                .build()

        "null targeting height and width"              | new Targeting.Builder().addTargeting(AD_UNIT_SIZE, INTERSECTS, [new LineItemSize(w: null, h: null)])
                                                                                .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [BANNER])
                                                                                .build()
    }

    def "PBS should invalidate line items with not supported '#matchingFunction' matching function by '#targetingType' targeting type"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, matchingFunction, [PBSUtils.randomString])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't had PG deals auction as line item hasn't passed validation"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        matchingFunction | targetingType
        INTERSECTS       | SITE_DOMAIN
        WITHIN           | SITE_DOMAIN
        INTERSECTS       | REFERRER
        WITHIN           | REFERRER
        INTERSECTS       | APP_BUNDLE
        WITHIN           | APP_BUNDLE
        INTERSECTS       | AD_UNIT_AD_SLOT
        WITHIN           | AD_UNIT_AD_SLOT
    }

    def "PBS should support line item targeting by string '#targetingType' targeting type"() {
        given: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(targetingType, MATCHES, stringTargetingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        targetingType  | bidRequest

        REFERRER       | BidRequest.defaultBidRequest.tap {
            site.page = stringTargetingValue
        }

        APP_BUNDLE     | BidRequest.defaultBidRequest.tap {
            app = new App(id: PBSUtils.randomString,
                    bundle: stringTargetingValue)
        }

        UFPD_BUYER_UID | BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                buyeruid = stringTargetingValue
            }
        }
    }

    def "PBS should support targeting matching by bidder parameters"() {
        given: "Bid request with specified bidder parameter"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                banner = Banner.defaultBanner
                ext = ImpExt.defaultImpExt
                ext.prebid.bidder = new Bidder(rubicon: Rubicon.default.tap { accountId = integerTargetingValue })
            }]
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].source = RUBICON.name().toLowerCase()
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(BIDP_ACCOUNT_ID, INTERSECTS, [integerTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should support line item targeting by page position targeting type"() {
        given: "Bid request and bid response"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.pos = integerTargetingValue
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(PAGE_POSITION, IN, [integerTargetingValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should support line item targeting by userdow targeting type"() {
        given: "Bid request and bid response"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def weekDay = ZonedDateTime.now(ZoneId.from(UTC)).dayOfWeek.get(SUNDAY_START.dayOfWeek())
            user = User.defaultUser.tap {
                ext = new UserExt(time: new UserTime(userdow: weekDay))
            }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(DOW, IN, [ZonedDateTime.now(ZoneId.from(UTC)).dayOfWeek.get(SUNDAY_START.dayOfWeek())])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should support line item targeting by userhour targeting type"() {
        given: "Bid request and bid response"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def hour = ZonedDateTime.now(ZoneId.from(UTC)).hour
            user = User.defaultUser.tap {
                ext = new UserExt(time: new UserTime(userhour: hour))
            }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(HOUR, IN, [ZonedDateTime.now(ZoneId.from(UTC)).hour])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
    }

    def "PBS should support line item targeting by '#targetingType' targeting type"() {
        given: "Bid request and bid response"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(HOUR, IN, [ZonedDateTime.now(ZoneId.from(UTC)).hour])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()

        where:
        targetingType                       | targetingValue

        "'\$or' root node with one match"   | new Targeting.Builder(OR).addTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])
                                                                       .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [VIDEO])
                                                                       .build()

        "'\$not' root node without matches" | new Targeting.Builder(NOT).buildNotBooleanOperatorTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [VIDEO])
    }

    def "PBS should support line item domain targeting by #domainTargetingType"() {
        given: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SITE_DOMAIN, MATCHES, stringTargetingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemSize = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemSize

        and: "Targeting recorded as matched"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedDomainTargeting?.size() == lineItemSize

        where:
        domainTargetingType     | bidRequest

        "site domain"           | BidRequest.defaultBidRequest.tap {
            site.domain = stringTargetingValue
        }

        "site publisher domain" | BidRequest.defaultBidRequest.tap {
            site.publisher = Publisher.defaultPublisher.tap { domain = stringTargetingValue }
        }
    }

    def "PBS should support line item domain targeting"() {
        given: "Bid response"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = siteDomain
            site.publisher = Publisher.defaultPublisher.tap { domain = sitePublisherDomain }
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(SITE_DOMAIN, IN, [siteDomain])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemSize = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemSize

        and: "Targeting recorded as matched"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedDomainTargeting?.size() == lineItemSize

        where:
        siteDomain                | sitePublisherDomain
        "www.example.com"         | null
        "https://www.example.com" | null
        "www.example.com"         | "example.com"
    }

    def "PBS should appropriately match '\$or', '\$not' line items targeting root node rules"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = targeting
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't had PG deals auction as targeting differs"
        assert !auctionResponse.ext?.debug?.pgmetrics

        where:
        targeting << [new Targeting.Builder(OR).addTargeting(AD_UNIT_SIZE, INTERSECTS, [new LineItemSize(w: PBSUtils.randomNumber, h: PBSUtils.randomNumber)])
                                               .addTargeting(AD_UNIT_MEDIA_TYPE, INTERSECTS, [VIDEO])
                                               .build(),
                      new Targeting.Builder(NOT).buildNotBooleanOperatorTargeting(AD_UNIT_SIZE, INTERSECTS, [LineItemSize.defaultLineItemSize])]
    }

    def "PBS should support line item targeting by device geo region, metro when request region, metro as int or str value are given"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(geo: new Geo(ext: new GeoExt(netAcuity: new GeoExtNetAcuity(region: requestValue,
                    metro: requestValue))))
        }

        and: "Planner response"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(DEVICE_REGION, IN, [lineItemValue])
                                              .addTargeting(DEVICE_METRO, IN, [lineItemValue])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == plansResponse.lineItems.size()
        assert auctionResponse.ext.debug.pgmetrics.matchedWholeTargeting.first() == plansResponse.lineItems.first().lineItemId

        where:
        requestValue          | lineItemValue
        stringTargetingValue  | stringTargetingValue
        integerTargetingValue | integerTargetingValue as String
    }

    def "PBS should be able to match Ad Slot targeting taken from different sources by MATCHES matching function"() {
        given: "Bid request with set ad slot info in different request places"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                ext.context = new ImpExtContext(data: new ImpExtContextData(pbAdSlot: contextAdSlot,
                        adServer: new ImpExtContextDataAdServer(adSlot: contextAdServerAdSlot)))
                ext.data = new ImpExtContextData(pbAdSlot: adSlot,
                        adServer: new ImpExtContextDataAdServer(adSlot: adServerAdSlot))
            }]
        }

        and: "Planner response with MATCHES one of Ad Slot values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(AD_UNIT_AD_SLOT, MATCHES, stringTargetingValue)
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemSize = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemSize

        where:
        contextAdSlot         | contextAdServerAdSlot | adSlot                | adServerAdSlot
        stringTargetingValue  | PBSUtils.randomString | PBSUtils.randomString | PBSUtils.randomString
        PBSUtils.randomString | stringTargetingValue  | PBSUtils.randomString | PBSUtils.randomString
        PBSUtils.randomString | PBSUtils.randomString | stringTargetingValue  | PBSUtils.randomString
        PBSUtils.randomString | PBSUtils.randomString | PBSUtils.randomString | stringTargetingValue
    }

    def "PBS should be able to match Ad Slot targeting taken from different sources by IN matching function"() {
        given: "Bid request with set ad slot info in different request places"
        def contextAdSlot = PBSUtils.randomString
        def contextAdServerAdSlot = PBSUtils.randomString
        def adSlot = PBSUtils.randomString
        def adServerAdSlot = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp = [Imp.defaultImpression.tap {
                ext.context = new ImpExtContext(data: new ImpExtContextData(pbAdSlot: contextAdSlot,
                        adServer: new ImpExtContextDataAdServer(adSlot: contextAdServerAdSlot)))
                ext.data = new ImpExtContextData(pbAdSlot: adSlot,
                        adServer: new ImpExtContextDataAdServer(adSlot: adServerAdSlot))
            }]
        }

        and: "Planner response with IN all of Ad Slot values"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].targeting = Targeting.defaultTargetingBuilder
                                              .addTargeting(AD_UNIT_AD_SLOT, IN, [contextAdSlot, contextAdServerAdSlot, adSlot, adServerAdSlot, PBSUtils.randomString])
                                              .build()
        }
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemSize = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS had PG auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.matchedWholeTargeting?.size() == lineItemSize
    }
}
