/*
 *
 *   Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 * /
 */

package org.wso2.carbon.identity.openidconnect;

import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.common.testng.WithCarbonHome;
import org.wso2.carbon.identity.common.testng.WithH2Database;
import org.wso2.carbon.identity.common.testng.WithRealmService;
import org.wso2.carbon.identity.common.testng.WithRegistry;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.oauth.tokenprocessor.HashingPersistenceProcessor;
import org.wso2.carbon.identity.oauth.tokenprocessor.TokenPersistenceProcessor;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.TestConstants;
import org.wso2.carbon.identity.openidconnect.dao.RequestObjectDAOImpl;
import org.wso2.carbon.identity.openidconnect.model.RequestedClaim;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WithCarbonHome
@WithRegistry
@WithRealmService
@WithH2Database(jndiName = "jdbc/WSO2IdentityDB",
        files = {"dbScripts/h2_with_application_and_token.sql", "dbScripts/identity.sql"})
public class RequestObjectServiceTest extends PowerMockTestCase {

    private static final String consumerKey = TestConstants.CLIENT_ID;
    private static final String sessionKey = "d43e8da324a33bdc941b9b95cad6a6a2";
    private static final String token = "4bdc941b93e8da324dc941b93ea2d";
    private static final String tokenId = "2sa9a678f890877856y66e75f605d456";
    private static final String invalidTokenId = "77856y6690875f605d456e2sa9a678f8";
    RequestedClaim requestedClaim = new RequestedClaim();

    private RequestObjectService requestObjectService;
    private List<List<RequestedClaim>> requestedEssentialClaims;

    @BeforeClass
    public void setUp() {

        requestObjectService = new RequestObjectService();
        requestedEssentialClaims = new ArrayList<>();
        List lstRequestedClams = new ArrayList<>();
        List values = new ArrayList<>();
        requestedEssentialClaims = new ArrayList<>();

        requestedClaim.setName("email");
        requestedClaim.setType("userinfo");
        requestedClaim.setValue("value1");
        requestedClaim.setEssential(true);
        requestedClaim.setValues(values);
        values.add("val1");
        values.add("val2");
        requestedClaim.setValues(values);
        lstRequestedClams.add(requestedClaim);
        requestedEssentialClaims.add(lstRequestedClams);
    }

    @Test
    public void testAddRequestObject() throws Exception {

        requestObjectService.addRequestObject(consumerKey, sessionKey, requestedEssentialClaims);
        List<RequestedClaim> claims = requestObjectService.
                getRequestedClaimsForSessionDataKey(sessionKey, true);
        Assert.assertEquals(claims.get(0).getName(), "email");
    }

    @Test
    public void testGetRequestedClaimsForUserInfo() throws Exception {

        RequestObjectDAOImpl requestObjectDAO = new RequestObjectDAOImpl();
        requestObjectService.addRequestObject(consumerKey, sessionKey, requestedEssentialClaims);
        requestObjectDAO.updateRequestObjectReferencebyTokenId(sessionKey, tokenId);
        addToken(token, tokenId);
        List<RequestedClaim> claims = requestObjectService.getRequestedClaimsForUserInfo(token);
        Assert.assertEquals(claims.get(0).getName(), "email");
    }

    @Test
    public void testGetRequestedClaimsForUserInfoException() throws Exception {

        String tableName = "IDN_OIDC_REQ_OBJECT_REFERENCE";
        try {
            RequestObjectDAOImpl requestObjectDAO = new RequestObjectDAOImpl();
            requestObjectService.addRequestObject(consumerKey, sessionKey, requestedEssentialClaims);
            requestObjectDAO.updateRequestObjectReferencebyTokenId(sessionKey, invalidTokenId);
            addToken(token, tokenId);
            List<RequestedClaim> claims = requestObjectService.getRequestedClaimsForUserInfo(token);
            Assert.assertEquals(claims.get(0).getName(), "email");
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Can not update code id or the access token id of the " +
                    "table ." + tableName);
        }
    }

    @Test
    public void testGetRequestedClaimsForIDToken() throws Exception {

        RequestObjectDAOImpl requestObjectDAO = new RequestObjectDAOImpl();
        requestObjectService.addRequestObject(consumerKey, sessionKey, requestedEssentialClaims);
        requestObjectDAO.updateRequestObjectReferencebyTokenId(sessionKey, tokenId);
        updateUserInfo(sessionKey, false);
        addToken(token, tokenId);
        List<RequestedClaim> claims = requestObjectService.getRequestedClaimsForIDToken(token);
        Assert.assertEquals(claims.get(0).getName(), "email");
    }

    protected void addToken(String token, String tokenId) throws Exception {

        TokenPersistenceProcessor hashingPersistenceProcessor = new HashingPersistenceProcessor();
        try (Connection connection = IdentityDatabaseUtil.getDBConnection()) {
            String sql = "UPDATE IDN_OAUTH2_ACCESS_TOKEN SET ACCESS_TOKEN_HASH=? WHERE TOKEN_ID=?";
            PreparedStatement prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, hashingPersistenceProcessor.getProcessedAccessTokenIdentifier(token));
            prepStmt.setString(2, tokenId);
            prepStmt.executeUpdate();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            String errorMsg = "Error occurred while inserting tokenID: " + token;
            throw new IdentityOAuth2Exception(errorMsg, e);
        }
    }

    protected void updateUserInfo(String sessionKey, boolean isUserInfo) throws Exception {

        try (Connection connection = IdentityDatabaseUtil.getDBConnection()) {
            String sql = "UPDATE IDN_OIDC_REQ_OBJECT_CLAIMS SET IS_USERINFO=? WHERE REQ_OBJECT_ID=" +
                    "(SELECT ID FROM IDN_OIDC_REQ_OBJECT_REFERENCE WHERE SESSION_DATA_KEY=?)";
            PreparedStatement prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, isUserInfo ? "1" : "0");
            prepStmt.setString(2, sessionKey);
            prepStmt.executeUpdate();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            String errorMsg = "Error occurred while inserting tokenID: " + token;
            throw new IdentityOAuth2Exception(errorMsg, e);
        }
    }
}
